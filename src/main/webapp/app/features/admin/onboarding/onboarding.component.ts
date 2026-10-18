import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, pairwise, startWith } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { EmployeeService } from 'app/features/employee';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  BadgeComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  ProgressComponent,
  SkeletonComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { EmployeeProcessSummary, InitiateOnboardingRequest, OnboardingService, addDays, daysBetween, localIsoDate } from '../index';

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
 * Onboarding — new hires whose onboarding checklist is still open.
 *
 * Overview (active, overdue, due this week, average completion) and one row per
 * process (`GET /api/hr/workflows/onboarding`): the new hire, start and due date,
 * checklist progress and status; a row opens the checklist. "Start onboarding"
 * (`MANAGE_ONBOARDING`, plus `READ_EMPLOYEE` to search for the employee) opens a
 * panel to pick the employee, the start date, the finish-by date (presets for one
 * week, two weeks, 30 days; task due dates are spread across that window) and notes. Completed and cancelled
 * onboardings drop off this list; they stay reachable from their own page.
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

  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_ONBOARDING);
  protected readonly canStart =
    this.account.hasPermission(Permission.MANAGE_ONBOARDING) && this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();

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
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);

  constructor() {
    this.load();
    if (this.canStart) {
      this.onboardingService.checklist().subscribe({ next: c => this.defaultWindow.set(c.defaultDays), error: () => undefined });
    }
    // Moving the start date moves the finish-by date with it, keeping the chosen length.
    this.form.controls.startDate.valueChanges
      .pipe(startWith(this.form.controls.startDate.value), pairwise(), takeUntilDestroyed())
      .subscribe(([before, after]) => {
        const target = this.form.controls.targetDate.value;
        if (before && after && target) this.form.controls.targetDate.setValue(addDays(target, daysBetween(before, after)));
      });
  }

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

  protected openStart(): void {
    this.form.reset({ employeeId: '', startDate: this.today, targetDate: addDays(this.today, this.defaultWindow()), notes: '' });
    this.formOpen.set(true);
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected load(): void {
    this.error.set(null);
    this.onboardingService.listActive({ page: 0, size: 100 }).subscribe({
      next: page => this.rows.set([...page.content].sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))),
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

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
