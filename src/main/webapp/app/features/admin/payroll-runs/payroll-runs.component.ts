import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FormFieldComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  SwitchComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { InitiatePayrollRunRequest, PayrollCalendar, PayrollCalendarService, PayrollRunService } from '../index';

/**
 * Start a payroll run (HR/Payroll admin) — the create half of P6.2. Picks an
 * open period (sourced from `GET /api/payroll/calendars/active`, which embeds
 * each calendar's upcoming periods) and initiates a run via
 * `POST /api/payroll/runs`, then navigates to the existing run-detail.
 *
 * There is **no runs-list endpoint** (runs are a process resource fetched by id
 * through `summary`), so this screen has no list — it starts a run and hands off
 * to `/payroll/runs/:id`. The create form is gated on `CREATE_PAYROLL_RUN`;
 * sourcing periods additionally needs `CONFIGURE_PAYROLL_CALENDAR`, surfaced as
 * an error state when absent.
 */
@Component({
  selector: 'hum-payroll-runs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    FormFieldComponent,
    SelectComponent,
    TextareaComponent,
    SwitchComponent,
    ButtonComponent,
    AlertComponent,
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

  private readonly calendars = signal<PayrollCalendar[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  /** Open (non-closed) periods across all active calendars, as picker options. */
  protected readonly periodOptions = computed<SelectOption[]>(() => {
    const opts: SelectOption[] = [{ value: '', label: this.translate.instant('humano.payrollRuns.selectPeriod') }];
    for (const cal of this.calendars()) {
      for (const p of cal.upcomingPeriods) {
        if (p.closed) continue;
        opts.push({ value: p.id, label: `${cal.name} · ${p.code} (${p.startDate} → ${p.endDate})` });
      }
    }
    return opts;
  });

  /** True once loaded and no open period is available to run. */
  protected readonly noPeriods = computed(() => !this.loading() && !this.error() && this.periodOptions().length <= 1);

  protected readonly form = this.fb.nonNullable.group({
    periodId: ['', Validators.required],
    draftMode: [false],
    notes: ['', Validators.maxLength(1000)],
  });

  constructor() {
    if (this.canCreate) this.load();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.calendarService.active().subscribe({
      next: data => {
        this.calendars.set(data);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
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
        this.toast.success(this.translate.instant('humano.payrollRuns.initiated'));
        this.submitting.set(false);
        void this.router.navigate(['/payroll/runs', run.id]);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.submitting.set(false);
      },
    });
  }
}
