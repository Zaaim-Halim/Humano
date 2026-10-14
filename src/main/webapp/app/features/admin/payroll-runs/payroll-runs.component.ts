import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  SwitchComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { MoneyPipe, PayPeriodPipe } from 'app/shared/util/format.pipes';

import { InitiatePayrollRunRequest, PayrollCalendar, PayrollCalendarService, PayrollRun, PayrollRunService, RunStatus } from '../index';

/** Lifecycle order, for the per-run progress bar. */
const STAGES: RunStatus[] = [RunStatus.DRAFT, RunStatus.CALCULATED, RunStatus.APPROVED, RunStatus.POSTED];

/**
 * Payroll runs — the payroll history and where to start the next run.
 *
 * - Overview: last run's net pay (with change vs the previous run), people paid, employer cost
 *   and runs still in progress.
 * - Trend: net pay and deductions per pay period (oldest → newest).
 * - History (`GET /api/payroll/runs`): one card per run with its lifecycle progress
 *   (draft → calculated → approved → posted), headcount and totals; a card opens the run.
 * - "New run" (`CREATE_PAYROLL_RUN`) opens a panel listing the open periods.
 */
@Component({
  selector: 'hum-payroll-runs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    MoneyPipe,
    PayPeriodPipe,
    LucideAngularModule,
    PageHeaderComponent,
    FormFieldComponent,
    SelectComponent,
    TextareaComponent,
    SwitchComponent,
    ButtonComponent,
    BadgeComponent,
    DrawerComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './payroll-runs.component.html',
})
export default class PayrollRunsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly runService = inject(PayrollRunService);
  private readonly calendarService = inject(PayrollCalendarService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canCreate = this.account.hasPermission(Permission.CREATE_PAYROLL_RUN);
  protected readonly stages = STAGES;

  // History.
  protected readonly runs = signal<PayrollRun[] | null>(null);
  protected readonly runsError = signal<string | null>(null);
  protected readonly latest = computed(() => this.runs()?.[0] ?? null);
  protected readonly previous = computed(() => this.runs()?.[1] ?? null);
  protected readonly netChange = computed(() => {
    const [a, b] = [this.latest(), this.previous()];
    return a && b && b.totalNet ? ((a.totalNet - b.totalNet) / b.totalNet) * 100 : null;
  });
  protected readonly inProgress = computed(() => (this.runs() ?? []).filter(r => r.status !== RunStatus.POSTED).length);
  /** Trend bars, oldest first; heights relative to the largest gross. */
  protected readonly trend = computed(() => {
    const list = [...(this.runs() ?? [])].reverse().slice(-12);
    const max = Math.max(1, ...list.map(r => r.totalGross));
    return list.map(r => ({
      run: r,
      net: (r.totalNet / max) * 100,
      deductions: (r.totalDeductions / max) * 100,
    }));
  });

  // New run panel.
  protected readonly formOpen = signal(false);
  private readonly calendars = signal<PayrollCalendar[]>([]);
  protected readonly calendarsLoading = signal(false);
  protected readonly calendarsError = signal<string | null>(null);
  protected readonly submitting = signal(false);

  /** Open (non-closed) periods across all active calendars, as picker options. */
  protected readonly periodOptions = computed(() => {
    const opts = [{ value: '', label: this.label('humano.payrollRuns.selectPeriod') }];
    for (const cal of this.calendars()) {
      for (const p of cal.upcomingPeriods) {
        if (p.closed) continue;
        const month = new Date(`${p.startDate}T00:00:00`).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
        opts.push({ value: p.id, label: `${month} · ${cal.name}` });
      }
    }
    return opts;
  });
  protected readonly noPeriods = computed(() => !this.calendarsLoading() && !this.calendarsError() && this.periodOptions().length <= 1);

  protected readonly form = this.fb.nonNullable.group({
    periodId: ['', Validators.required],
    draftMode: [false],
    notes: ['', Validators.maxLength(1000)],
  });

  constructor() {
    this.loadRuns();
  }

  /** Index of a status in the lifecycle (0–3). */
  protected stageIndex(status: RunStatus): number {
    return STAGES.indexOf(status);
  }

  protected openRun(run: PayrollRun): void {
    void this.router.navigate(['/payroll/runs', run.id]);
  }

  protected openNewRun(): void {
    this.form.reset({ periodId: '', draftMode: false, notes: '' });
    this.formOpen.set(true);
    if (!this.calendars().length) this.loadCalendars();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected loadRuns(): void {
    this.runsError.set(null);
    this.runService.list({ page: 0, size: 24 }).subscribe({
      next: page => this.runs.set(page.content),
      error: (err: unknown) => this.runsError.set(normalizeHttpError(err)),
    });
  }

  protected loadCalendars(): void {
    this.calendarsLoading.set(true);
    this.calendarsError.set(null);
    this.calendarService.active().subscribe({
      next: data => {
        this.calendars.set(data);
        this.calendarsLoading.set(false);
      },
      error: (err: unknown) => {
        this.calendarsError.set(normalizeHttpError(err));
        this.calendarsLoading.set(false);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: InitiatePayrollRunRequest = {
      periodId: raw.periodId,
      draftMode: raw.draftMode,
      ...(raw.notes.trim() ? { notes: raw.notes.trim() } : {}),
    };
    this.submitting.set(true);
    this.runService.initiate(body).subscribe({
      next: run => {
        this.toast.success(this.label('humano.payrollRuns.initiated'));
        this.submitting.set(false);
        this.formOpen.set(false);
        void this.router.navigate(['/payroll/runs', run.id]);
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
