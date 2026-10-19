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
import { MoneyPipe } from 'app/shared/util/format.pipes';

import {
  ACTIVE_PROCESS_STATUSES,
  OffboardingProcess,
  OffboardingProgress,
  OffboardingService,
  OffboardingTask,
  daysBetween,
  localIsoDate,
} from '../index';

/** Hand-over signals shown in the side column, in display order. */
const HANDOVER: { key: keyof OffboardingProgress; icon: string }[] = [
  { key: 'knowledgeTransferCompleted', icon: 'file-text' },
  { key: 'assetsReturned', icon: 'laptop' },
  { key: 'accessRevoked', icon: 'ban' },
  { key: 'pendingRequestsProcessed', icon: 'receipt' },
  { key: 'benefitsTerminated', icon: 'heart-pulse' },
  { key: 'exitInterviewConducted', icon: 'message-square' },
  { key: 'settlementCalculated', icon: 'wallet' },
];

const UNASSIGNED = 'unassigned';

/**
 * One offboarding — a leaver's hand-over (`GET /api/hr/workflows/offboarding/{id}`).
 *
 * The header gives the last working day with a countdown ("in 12 days", "left 3 days ago") and a
 * track from the start of the offboarding to that day, with every task on its due day. The
 * checklist is grouped into overdue / to do / done and can be narrowed to one owner (the leaver,
 * their exit interviewer, HR/IT). The side column holds overdue tasks per owner (escalation to
 * their managers), key dates and the termination reason, the final settlement (approved
 * expense claims; leave and overtime aren't tracked yet) and hand-over signals. With
 * `MANAGE_OFFBOARDING`, tasks are ticked off and the offboarding can be cancelled — before the
 * last day that restores the employee's status; after it they stay terminated.
 */
@Component({
  selector: 'hum-offboarding-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    MoneyPipe,
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
  templateUrl: './offboarding-detail.component.html',
})
export default class OffboardingDetailComponent {
  /** Route param. */
  readonly id = input.required<string>();

  private readonly offboardingService = inject(OffboardingService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_OFFBOARDING);
  protected readonly canOpenProfile = this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();
  protected readonly unassigned = UNASSIGNED;

  protected readonly process = signal<OffboardingProcess | null>(null);
  protected readonly employee = signal<EmployeeProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly ownerFilter = signal<string>('');

  protected readonly active = computed(() => {
    const p = this.process();
    return !!p && ACTIVE_PROCESS_STATUSES.includes(p.processStatus);
  });
  /** Days until the last working day (negative once it has passed). */
  protected readonly daysToLastDay = computed(() => {
    const p = this.process();
    return p ? daysBetween(this.today, p.lastWorkingDate) : 0;
  });
  protected readonly tasks = computed(() =>
    [...(this.process()?.tasks ?? [])].sort(
      (a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999') || a.title.localeCompare(b.title),
    ),
  );
  protected readonly overdue = computed(() => this.tasks().filter(t => this.isOverdue(t)));

  /** The offboarding window (start → last working day) and where today falls in it. */
  protected readonly timeline = computed(() => {
    const p = this.process();
    if (!p) return null;
    const total = Math.max(1, daysBetween(p.startDate, p.lastWorkingDate));
    const elapsed = Math.min(total, Math.max(0, daysBetween(p.startDate, this.today)));
    return { total, elapsed, todayPct: (elapsed / total) * 100 };
  });
  protected readonly markers = computed(() => {
    const p = this.process();
    const tl = this.timeline();
    if (!p || !tl) return [];
    const byDay = new Map<number, OffboardingTask[]>();
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
  protected readonly groups = computed(() => {
    const owner = this.ownerFilter();
    const visible = this.tasks().filter(t => !owner || (t.assignedToId ?? UNASSIGNED) === owner);
    return [
      { key: 'overdue', tasks: visible.filter(t => this.isOverdue(t)) },
      { key: 'todo', tasks: visible.filter(t => !t.completed && !this.isOverdue(t)) },
      { key: 'done', tasks: visible.filter(t => t.completed) },
    ].filter(group => group.tasks.length);
  });
  protected readonly handover = computed(() => {
    const progress = this.process()?.progress;
    return progress ? HANDOVER.map(h => ({ ...h, done: progress[h.key] === true })) : [];
  });

  // Dialogs.
  protected readonly completing = signal<OffboardingTask | null>(null);
  protected readonly note = signal('');
  protected readonly saving = signal(false);
  protected readonly cancelOpen = signal(false);
  protected readonly cancelReason = signal('');
  protected readonly escalating = signal(false);

  constructor() {
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected isOverdue(task: OffboardingTask): boolean {
    return !task.completed && !!task.dueDate && task.dueDate < this.today;
  }

  protected dueLabel(task: OffboardingTask): string {
    if (!task.dueDate) return '';
    const days = daysBetween(this.today, task.dueDate);
    if (days < 0) return this.label('humano.onboarding.overdueBy', { count: -days });
    if (days === 0) return this.label('humano.onboarding.dueToday');
    if (days === 1) return this.label('humano.onboarding.dueTomorrow');
    return this.label('humano.onboarding.dueIn', { count: days });
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    this.offboardingService.find(id).subscribe({
      next: p => {
        this.process.set(p);
        if (this.canOpenProfile && this.employee()?.id !== p.employeeId) {
          this.employeeService.find(p.employeeId).subscribe({ next: e => this.employee.set(e), error: () => this.employee.set(null) });
        }
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected openComplete(task: OffboardingTask): void {
    if (!this.canManage || !this.active() || task.completed) return;
    this.note.set('');
    this.completing.set(task);
  }

  protected complete(): void {
    const task = this.completing();
    if (!task) return;
    this.saving.set(true);
    const note = this.note().trim();
    this.offboardingService
      .completeTask(this.id(), { taskId: task.taskId, successful: true, ...(note ? { completionNotes: note } : {}) })
      .subscribe({
        next: p => {
          this.process.set(p);
          this.saving.set(false);
          this.completing.set(null);
          this.toast.success(
            this.label(p.completionPercentage === 100 ? 'humano.offboarding.allDone' : 'humano.onboarding.taskDone', { task: task.title }),
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
    this.offboardingService.escalate(this.id()).subscribe({
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
    if (!reason) return;
    this.saving.set(true);
    this.offboardingService.cancel(this.id(), reason).subscribe({
      next: () => {
        this.saving.set(false);
        this.cancelOpen.set(false);
        this.toast.success(this.label('humano.offboarding.cancelled'));
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
