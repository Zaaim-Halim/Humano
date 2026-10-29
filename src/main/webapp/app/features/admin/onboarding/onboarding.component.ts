import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, pairwise, startWith } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { EmployeeService } from 'app/features/employee';
import { PlanFeature, planIncludes } from 'app/features/platform/models/entitlements.model';
import { MeBillingService } from 'app/features/platform/services/me-billing.service';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  ProgressComponent,
  SkeletonComponent,
  TabItem,
  TabsComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import {
  EmployeeProcessStatus,
  EmployeeProcessSummary,
  InitiateOnboardingRequest,
  OnboardingService,
  Training,
  TrainingService,
  addDays,
  daysBetween,
  localIsoDate,
} from '../index';

/** Active (planned / in progress / delayed) or finished (completed / cancelled) onboardings. */
type View = 'active' | 'history';
const HISTORY_STATUSES = [EmployeeProcessStatus.COMPLETED, EmployeeProcessStatus.CANCELLED];

/** Onboarding length until the company default has loaded (the backend default). */
const FALLBACK_WINDOW = 30;

/** Quick picks for how long the onboarding should take; the company default is added. */
const WINDOW_PRESETS = [7, 14, 30];

/** Group validator: the finish-by date can't be before the start date. */
function targetNotBeforeStart(group: AbstractControl): ValidationErrors | null {
  const { startDate, targetDate } = group.getRawValue() as { startDate: string; targetDate: string };
  return startDate && targetDate && targetDate < startDate ? { targetBeforeStart: true } : null;
}

/**
 * Onboarding — new hires' checklists, in two tabs (`?view=`):
 *
 * - Active: overview (in progress, behind schedule, due this week, average completion) and one
 *   row per open process — the new hire, start and due date, progress and status.
 * - History: completed and cancelled onboardings, most recently finished first, with when they
 *   finished and, for cancelled ones, the reason.
 *
 * A row opens the checklist. "Start onboarding" (`MANAGE_ONBOARDING`, plus `READ_EMPLOYEE` to
 * search for the employee) opens a panel to pick the employee, the start date, the finish-by
 * date (company default, one week, two weeks, 30 days; task due dates are spread across that
 * window), trainings to enroll them in (Training module only) and notes. `?start=<employeeId>`
 * (from an employee's profile) opens the panel with that person picked.
 */
@Component({
  selector: 'hum-onboarding',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    PageHeaderComponent,
    FormFieldComponent,
    AutocompleteComponent,
    InputComponent,
    TextareaComponent,
    ButtonComponent,
    BadgeComponent,
    ProgressComponent,
    CheckboxComponent,
    TabsComponent,
    DrawerComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
  ],
  templateUrl: './onboarding.component.html',
})
export default class OnboardingComponent {
  private readonly fb = inject(FormBuilder);
  private readonly onboardingService = inject(OnboardingService);
  private readonly employeeService = inject(EmployeeService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);
  private readonly route = inject(ActivatedRoute);
  private readonly trainingService = inject(TrainingService);
  private readonly meBilling = inject(MeBillingService);

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_ONBOARDING);
  protected readonly canStart =
    this.account.hasPermission(Permission.MANAGE_ONBOARDING) && this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();

  protected readonly view = signal<View>(this.route.snapshot.queryParamMap.get('view') === 'history' ? 'history' : 'active');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'active', label: this.label('humano.onboarding.tabActive'), icon: 'user-plus' },
    { id: 'history', label: this.label('humano.onboarding.tabHistory'), icon: 'history' },
  ]);
  protected readonly rows = signal<EmployeeProcessSummary[] | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly stats = computed(() => {
    const list = this.rows() ?? [];
    const weekAhead = localIsoDate(new Date(Date.now() + 7 * 86400000));
    return {
      active: list.length,
      overdue: list.filter(r => this.isOverdue(r)).length,
      dueSoon: list.filter(r => !!r.dueDate && r.dueDate >= this.today && r.dueDate <= weekAhead).length,
      average: list.length ? Math.round(list.reduce((sum, r) => sum + r.completionPercentage, 0) / list.length) : 0,
    };
  });

  // Start panel.
  protected readonly formOpen = signal(false);
  protected readonly submitting = signal(false);
  /** Company default onboarding length (`GET /onboarding-checklist`). */
  protected readonly defaultWindow = signal(FALLBACK_WINDOW);
  protected readonly presets = computed(() => [...new Set([...WINDOW_PRESETS, this.defaultWindow()])].sort((a, b) => a - b));
  protected readonly form = this.fb.nonNullable.group(
    {
      employeeId: ['', Validators.required],
      startDate: [this.today, Validators.required],
      targetDate: [addDays(this.today, FALLBACK_WINDOW), Validators.required],
      notes: ['', Validators.maxLength(2000)],
    },
    { validators: targetNotBeforeStart },
  );
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  /** Length of the chosen window in days, or null while a date is missing. */
  protected readonly windowDays = computed(() => {
    const { startDate, targetDate } = this.formValue();
    return startDate && targetDate ? daysBetween(startDate, targetDate) : null;
  });
  /** Pre-picked employee when opened from a profile (`?start=`). */
  protected readonly startFor = signal<AutocompleteOption | null>(null);

  // Trainings to enroll the new hire in — only with the Training module.
  protected readonly trainingsAvailable = computed(() => planIncludes(this.meBilling.entitlements(), PlanFeature.TRAINING));
  protected readonly trainings = signal<Training[] | null>(null);
  protected readonly pickedTrainings = signal<ReadonlySet<string>>(new Set());

  constructor() {
    this.load();
    if (this.canStart) {
      // Open `?start=` only once the company default is known, so the finish-by date is right.
      const startId = this.route.snapshot.queryParamMap.get('start');
      this.onboardingService.checklist().subscribe({
        next: c => {
          this.defaultWindow.set(c.defaultDays);
          if (startId) this.openStartFor(startId);
        },
        error: () => {
          if (startId) this.openStartFor(startId);
        },
      });
    }
    // Moving the start date moves the finish-by date with it, keeping the chosen length.
    this.form.controls.startDate.valueChanges
      .pipe(startWith(this.form.controls.startDate.value), pairwise(), takeUntilDestroyed())
      .subscribe(([before, after]) => {
        const target = this.form.controls.targetDate.value;
        if (before && after && target) this.form.controls.targetDate.setValue(addDays(target, daysBetween(before, after)));
      });
  }
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

  /** "1 week", "2 weeks", "30 days", else "N days"; the company default is marked. */
  protected presetLabel(days: number): string {
    const base = WINDOW_PRESETS.includes(days)
      ? this.label(`humano.onboarding.preset.${days}`)
      : this.label('humano.onboarding.presetDays', { count: days });
    return days === this.defaultWindow() ? this.label('humano.onboarding.presetDefault', { label: base }) : base;
  }

  protected setWindow(days: number): void {
    const start = this.form.controls.startDate.value;
    if (start) this.form.controls.targetDate.setValue(addDays(start, days));
  }

  /** Behind schedule: a task or the whole checklist is past its due date. */
  protected isOverdue(row: EmployeeProcessSummary): boolean {
    return row.overdueTasks > 0 || (!!row.dueDate && row.dueDate < this.today);
  }

  protected setView(view: string | undefined): void {
    const next: View = view === 'history' ? 'history' : 'active';
    if (next === this.view()) return;
    this.view.set(next);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: next === 'history' ? 'history' : null },
      replaceUrl: true,
    });
    this.load();
  }

  protected openStart(employee: AutocompleteOption | null = null, startDate = this.today): void {
    this.startFor.set(employee);
    this.pickedTrainings.set(new Set());
    this.form.reset({
      employeeId: employee?.value ?? '',
      startDate,
      targetDate: addDays(startDate, this.defaultWindow()),
      notes: '',
    });
    this.formOpen.set(true);
    if (this.trainingsAvailable() && this.trainings() === null) {
      this.trainingService.list({ page: 0, size: 100, sort: ['name,asc'] }).subscribe({
        next: page => this.trainings.set(page.content),
        error: () => this.trainings.set([]),
      });
    }
  }

  protected toggleTraining(id: string, on: boolean): void {
    this.pickedTrainings.update(set => {
      const next = new Set(set);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  /** History row: when it ended — completion date, or the last change for a cancelled one. */
  protected endedOn(row: EmployeeProcessSummary): string | null {
    return row.status === EmployeeProcessStatus.COMPLETED ? row.completionDate : row.lastModifiedDate;
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected load(): void {
    this.error.set(null);
    this.rows.set(null);
    const history = this.view() === 'history';
    const request$ = history
      ? this.onboardingService.list({ status: HISTORY_STATUSES }, { page: 0, size: 100, sort: ['lastModifiedDate,desc'] })
      : this.onboardingService.listActive({ page: 0, size: 100 });
    request$.subscribe({
      next: page =>
        this.rows.set(history ? page.content : [...page.content].sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.rows.set([]);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: InitiateOnboardingRequest = {
      employeeId: raw.employeeId,
      startDate: raw.startDate,
      targetDate: raw.targetDate,
      ...(this.pickedTrainings().size ? { requiredTrainingIds: [...this.pickedTrainings()] } : {}),
      ...(raw.notes.trim() ? { notes: raw.notes.trim() } : {}),
    };
    this.submitting.set(true);
    this.onboardingService.initiate(body).subscribe({
      next: process => {
        this.toast.success(this.label('humano.onboarding.started', { name: process.employeeName }));
        this.submitting.set(false);
        this.formOpen.set(false);
        void this.router.navigate(['/onboarding', process.processId]);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.submitting.set(false);
      },
    });
  }

  /** Opens the start panel for one employee (from their profile), then drops `?start=` from the URL. */
  private openStartFor(employeeId: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { start: null }, queryParamsHandling: 'merge', replaceUrl: true });
    this.employeeService.find(employeeId).subscribe({
      next: e => {
        const name = [e.firstName, e.lastName].filter(Boolean).join(' ');
        const start = e.startDate?.slice(0, 10);
        this.openStart({ value: e.id, label: name, sublabel: e.jobTitle }, start && start > this.today ? start : this.today);
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
