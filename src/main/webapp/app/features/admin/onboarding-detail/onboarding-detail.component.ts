import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { EmployeeService } from 'app/features/employee';
import type { EmployeeProfile } from 'app/features/employee/models/employee.model';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  FormFieldComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import {
  ACTIVE_PROCESS_STATUSES,
  OnboardingProcess,
  OnboardingProgress,
  OnboardingService,
  OnboardingTask,
  daysBetween,
  localIsoDate,
} from '../index';

/** Readiness flags shown in the side column, in display order. */
const READINESS: { key: keyof OnboardingProgress; icon: string }[] = [
  { key: 'profileCreated', icon: 'id-card' },
  { key: 'departmentAssigned', icon: 'building-2' },
  { key: 'systemAccessGranted', icon: 'laptop' },
  { key: 'documentsUploaded', icon: 'file-text' },
  { key: 'benefitsSetup', icon: 'heart-pulse' },
  { key: 'trainingsAssigned', icon: 'graduation-cap' },
];

/** Owner filter value for tasks nobody in particular owns (HR / IT). */
const UNASSIGNED = 'unassigned';

/**
 * One onboarding — the new hire's checklist (`GET /api/hr/workflows/onboarding/{id}`).
 *
 * The header places the hire on a track from their start date to the onboarding's finish-by
 * date (its length is chosen per onboarding): today's position, and every task as a marker on
 * its due day (done, open or overdue), so slippage reads at a glance. Below, the checklist is
 * grouped into overdue / to do / done and can be narrowed to one owner (the new hire, their
 * manager, HR/IT). The side column says what needs attention
 * (overdue tasks per owner, with escalation to their managers), the key dates, and readiness
 * signals from the employee's records. With `MANAGE_ONBOARDING`, open tasks are ticked off from
 * their check button (optional note) and the onboarding can be cancelled with a reason.
 */
@Component({
  selector: 'hum-onboarding-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    ButtonComponent,
    AvatarComponent,
    BadgeComponent,
    DialogComponent,
    FormFieldComponent,
    TextareaComponent,
    AlertComponent,
    SkeletonComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './onboarding-detail.component.html',
})
export default class OnboardingDetailComponent {
  /** Route param. */
  readonly id = input.required<string>();

  private readonly onboardingService = inject(OnboardingService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_ONBOARDING);
  protected readonly canOpenProfile = this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();
  protected readonly unassigned = UNASSIGNED;

  protected readonly process = signal<OnboardingProcess | null>(null);
  /** Job title, department and manager — only loaded with `READ_EMPLOYEE`. */
  protected readonly employee = signal<EmployeeProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly ownerFilter = signal<string>('');

  protected readonly active = computed(() => {
    const p = this.process();
    return !!p && ACTIVE_PROCESS_STATUSES.includes(p.processStatus);
  });
  /** Due-date order (the API returns tasks unordered); undated tasks last. */
  protected readonly tasks = computed(() =>
    [...(this.process()?.tasks ?? [])].sort(
      (a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999') || a.title.localeCompare(b.title),
    ),
  );
  protected readonly overdue = computed(() => this.tasks().filter(t => this.isOverdue(t)));

  /** Length of the checklist window and where today falls in it (day 0 = start date). */
  protected readonly timeline = computed(() => {
    const p = this.process();
    if (!p) return null;
    const total = Math.max(1, p.dueDate ? daysBetween(p.startDate, p.dueDate) : 30);
    const elapsed = Math.min(total, Math.max(0, daysBetween(p.startDate, this.today)));
    return { total, elapsed, daysLeft: Math.max(0, total - elapsed), todayPct: (elapsed / total) * 100 };
  });
  /** One marker per due day on the track; tasks sharing a day share a marker. */
  protected readonly markers = computed(() => {
    const p = this.process();
    const tl = this.timeline();
    if (!p || !tl) return [];
    const byDay = new Map<number, OnboardingTask[]>();
    for (const t of this.tasks()) {
      if (!t.dueDate) continue;
      const day = Math.min(tl.total, Math.max(0, daysBetween(p.startDate, t.dueDate)));
      byDay.set(day, [...(byDay.get(day) ?? []), t]);
    }
    return [...byDay.entries()].map(([day, list]) => ({
      day,
      pct: (day / tl.total) * 100,
      count: list.length,
      done: list.every(t => t.completed),
      overdue: list.some(t => this.isOverdue(t)),
      titles: list.map(t => t.title).join(', '),
    }));
  });

  /** Owners with their open-task counts, for the filter chips. */
  protected readonly owners = computed(() => {
    const map = new Map<string, { value: string; name: string; open: number }>();
    for (const t of this.tasks()) {
      const value = t.assignedToId ?? UNASSIGNED;
      const entry = map.get(value) ?? { value, name: t.assignedToName ?? this.label('humano.onboarding.hrOwned'), open: 0 };
      if (!t.completed) entry.open++;
      map.set(value, entry);
    }
    return [...map.values()];
  });
  /** Overdue tasks per owner, for the attention panel. */
  protected readonly overdueByOwner = computed(() => {
    const map = new Map<string, { name: string; count: number }>();
    for (const t of this.overdue()) {
      const key = t.assignedToId ?? UNASSIGNED;
      const entry = map.get(key) ?? { name: t.assignedToName ?? this.label('humano.onboarding.hrOwned'), count: 0 };
      entry.count++;
      map.set(key, entry);
    }
    return [...map.values()].sort((a, b) => b.count - a.count);
  });
  /** Filtered checklist, grouped by state (overdue, to do, done) in due-date order; empty groups are dropped. */
  protected readonly groups = computed(() => {
    const owner = this.ownerFilter();
    const visible = this.tasks().filter(t => !owner || (t.assignedToId ?? UNASSIGNED) === owner);
    return [
      { key: 'overdue', tasks: visible.filter(t => this.isOverdue(t)) },
      { key: 'todo', tasks: visible.filter(t => !t.completed && !this.isOverdue(t)) },
      { key: 'done', tasks: visible.filter(t => t.completed) },
    ].filter(group => group.tasks.length);
  });
  protected readonly readiness = computed(() => {
    const progress = this.process()?.progress;
    return progress ? READINESS.map(r => ({ ...r, done: progress[r.key] === true })) : [];
  });

  // Complete-task dialog.
  protected readonly completing = signal<OnboardingTask | null>(null);
  protected readonly note = signal('');
  protected readonly saving = signal(false);

  // Cancel dialog.
  protected readonly cancelOpen = signal(false);
  protected readonly cancelReason = signal('');
  protected readonly escalating = signal(false);

  constructor() {
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected isOverdue(task: OnboardingTask): boolean {
    return !task.completed && !!task.dueDate && task.dueDate < this.today;
  }

  /** Relative due label for an open task: overdue by, due today/tomorrow, due in. */
  protected dueLabel(task: OnboardingTask): string {
    if (!task.dueDate) return '';
    const days = daysBetween(this.today, task.dueDate);
    if (days < 0) return this.label('humano.onboarding.overdueBy', { count: -days });
    if (days === 0) return this.label('humano.onboarding.dueToday');
    if (days === 1) return this.label('humano.onboarding.dueTomorrow');
    return this.label('humano.onboarding.dueIn', { count: days });
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    this.onboardingService.find(id).subscribe({
      next: p => {
        this.process.set(p);
        if (this.canOpenProfile && this.employee()?.id !== p.employeeId) {
          this.employeeService.find(p.employeeId).subscribe({ next: e => this.employee.set(e), error: () => this.employee.set(null) });
        }
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected openComplete(task: OnboardingTask): void {
    if (!this.canManage || !this.active() || task.completed) return;
    this.note.set('');
    this.completing.set(task);
  }

  protected complete(): void {
    const task = this.completing();
    if (!task) return;
    this.saving.set(true);
    const note = this.note().trim();
    this.onboardingService
      .completeTask(this.id(), { taskId: task.taskId, successful: true, ...(note ? { completionNotes: note } : {}) })
      .subscribe({
        next: p => {
          this.process.set(p);
          this.saving.set(false);
          this.completing.set(null);
          this.toast.success(
            this.label(p.completionPercentage === 100 ? 'humano.onboarding.allDone' : 'humano.onboarding.taskDone', { task: task.title }),
          );
        },
        error: (err: unknown) => {
          this.toast.danger(normalizeHttpError(err));
          this.saving.set(false);
        },
      });
  }

  protected escalate(): void {
    this.escalating.set(true);
    this.onboardingService.escalate(this.id()).subscribe({
      next: () => {
        this.escalating.set(false);
        this.toast.success(this.label('humano.onboarding.escalated', { count: this.overdue().length }));
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.escalating.set(false);
      },
    });
  }

  protected openCancel(): void {
    this.cancelReason.set('');
    this.cancelOpen.set(true);
  }

  protected cancel(): void {
    const reason = this.cancelReason().trim();
    if (!reason) {
      this.toast.warning(this.label('humano.onboarding.reasonRequired'));
      return;
    }
    this.saving.set(true);
    this.onboardingService.cancel(this.id(), reason).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelOpen.set(false);
        this.toast.success(this.label('humano.onboarding.cancelled'));
        this.load();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
