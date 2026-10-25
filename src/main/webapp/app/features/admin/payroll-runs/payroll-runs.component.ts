import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, forkJoin, of, switchMap } from 'rxjs';

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
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  SwitchComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { MoneyPipe, PayPeriodPipe, PluralPipe, RunMoneyPipe } from 'app/shared/util/format.pipes';

import {
  InitiatePayrollRunRequest,
  PayrollCalendar,
  PayrollCalendarService,
  PayrollPeriodService,
  PayrollRun,
  PayrollRunService,
  RunStatus,
} from '../index';

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
    RunMoneyPipe,
    PluralPipe,
    PayPeriodPipe,
    LucideAngularModule,
    PageHeaderComponent,
    FormFieldComponent,
    InputComponent,
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
  private readonly periodService = inject(PayrollPeriodService);
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
    // Only between two single totals in the same currency.
    return a?.totalNet != null && b?.totalNet && a.currencyCode === b.currencyCode ? ((a.totalNet - b.totalNet) / b.totalNet) * 100 : null;
  });
  protected readonly inProgress = computed(() => (this.runs() ?? []).filter(r => r.status !== RunStatus.POSTED).length);
  /**
   * Trend bars, oldest first; heights relative to the largest gross. Runs paying people in several
   * currencies have no single total to plot and are left out.
   */
  protected readonly trend = computed(() => {
    const list = [...(this.runs() ?? [])]
      .reverse()
      .filter(r => r.totalGross !== null)
      .slice(-12);
    const max = Math.max(1, ...list.map(r => r.totalGross ?? 0));
    return list.map(r => ({
      run: r,
      net: ((r.totalNet ?? 0) / max) * 100,
      deductions: ((r.totalDeductions ?? 0) / max) * 100,
    }));
  });
  /** Runs left out of the trend because they pay people in several currencies. */
  protected readonly trendLeftOut = computed(() => (this.runs() ?? []).filter(r => r.totalGross === null).length);

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
  /** Loading calendars already needs payroll-calendar access, so whoever sees them can create periods. */
  protected readonly canCreatePeriods = computed(() => this.calendars().length > 0);
  protected readonly creatingPeriods = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    periodId: ['', Validators.required],
    paymentDate: [''],
    draftMode: [false],
    notes: ['', Validators.maxLength(1000)],
  });

  /** The selected period's stored payment date, to tell whether the form changed it. */
  private readonly storedPaymentDate = signal<string | null>(null);
  /** Paid in a later year than the period ends: templates that tax by payment date apply that year's rules. */
  protected readonly payYear = signal<string | null>(null);

  constructor() {
    this.loadRuns();
    // Picking a period shows its payment date, which can be moved before the run starts.
    this.form.controls.periodId.valueChanges.pipe(takeUntilDestroyed()).subscribe(id => {
      const period = this.calendars()
        .flatMap(c => c.upcomingPeriods)
        .find(p => p.id === id);
      this.storedPaymentDate.set(period?.paymentDate ?? null);
      this.form.controls.paymentDate.setValue(period?.paymentDate ?? '');
    });
    this.form.controls.paymentDate.valueChanges.pipe(takeUntilDestroyed()).subscribe(date => {
      const period = this.calendars()
        .flatMap(c => c.upcomingPeriods)
        .find(p => p.id === this.form.controls.periodId.value);
      const year = date.slice(0, 4);
      this.payYear.set(period && year && year !== period.endDate.slice(0, 4) ? year : null);
    });
  }

  /** Index of a status in the lifecycle (0–3). */
  protected stageIndex(status: RunStatus): number {
    return STAGES.indexOf(status);
  }

  protected openRun(run: PayrollRun): void {
    void this.router.navigate(['/payroll/runs', run.id]);
  }

  protected openNewRun(): void {
    this.form.reset({ periodId: '', paymentDate: '', draftMode: false, notes: '' });
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

  /** Creates each active calendar's periods up to three months ahead, then offers them in the picker. */
  protected createPeriods(): void {
    this.creatingPeriods.set(true);
    forkJoin(this.calendars().map(c => this.calendarService.createUpcomingPeriods(c.id))).subscribe({
      next: created => {
        this.toast.success(this.label('humano.payrollRuns.periodsCreated', { n: created.flat().length }));
        this.creatingPeriods.set(false);
        this.loadCalendars();
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.creatingPeriods.set(false);
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
    // A moved payment date is saved on the period first: it decides which year's rules apply.
    const moveDate$: Observable<unknown> =
      raw.paymentDate && raw.paymentDate !== this.storedPaymentDate()
        ? this.periodService.updatePaymentDate(raw.periodId, raw.paymentDate)
        : of(null);
    this.submitting.set(true);
    moveDate$.pipe(switchMap(() => this.runService.initiate(body))).subscribe({
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
