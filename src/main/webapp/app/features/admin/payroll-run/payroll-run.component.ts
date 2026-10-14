import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MoneyPipe, PayPeriodPipe } from 'app/shared/util/format.pipes';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { CurrentEmployeeService } from 'app/features/employee';
import {
  AlertComponent,
  ButtonComponent,
  DialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  StatTileComponent,
  StepperComponent,
  ToastService,
} from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

import { PayrollResult, PayrollRun, PayrollRunService, PayrollRunSummary, RunStatus } from '../index';

const STEP_ORDER: RunStatus[] = [RunStatus.DRAFT, RunStatus.CALCULATED, RunStatus.APPROVED, RunStatus.POSTED];

interface Holder<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}
const idle = <T>(): Holder<T> => ({ data: null, loading: false, error: null });

/**
 * Payroll Run (HR/Admin hero screen) — `/payroll/runs/:id`. Shows the run
 * summary (totals, variance vs previous period, by-department, top earners) and
 * per-employee results, with a lifecycle stepper and guarded approve/post.
 *
 * The run's status comes from `GET /api/payroll/runs/{id}` and drives the stepper and the
 * available actions; `summary.errorCount` counts validation errors, and the detailed
 * `validationErrors` arrive on calculation responses. Approval is recorded against the
 * signed-in user's employee record.
 */
@Component({
  selector: 'hum-payroll-run',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MoneyPipe,
    PayPeriodPipe,
    TranslatePipe,
    PageHeaderComponent,
    StepperComponent,
    StatTileComponent,
    ButtonComponent,
    DialogComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './payroll-run.component.html',
})
export default class PayrollRunComponent {
  /** Run id from the route (`/payroll/runs/:id`). */
  readonly id = input.required<string>();

  private readonly runService = inject(PayrollRunService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly summary = signal<Holder<PayrollRunSummary>>(idle());
  protected readonly results = signal<Holder<PayrollResult[]>>(idle());
  /** The run itself — status, totals and (after a calculation) validation errors. */
  protected readonly run = signal<PayrollRun | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirm = signal<'approve' | 'post' | null>(null);

  protected readonly steps = computed(() => [
    this.translate.instant('humano.payrollRun.stepDraft'),
    this.translate.instant('humano.payrollRun.stepCalculated'),
    this.translate.instant('humano.payrollRun.stepApproved'),
    this.translate.instant('humano.payrollRun.stepPosted'),
  ]);
  protected readonly currentStep = computed(() => {
    const status = this.run()?.status;
    const i = status ? STEP_ORDER.indexOf(status) : 0;
    return i < 0 ? 0 : i;
  });
  protected readonly byDepartment = computed(() => Object.entries(this.summary().data?.byDepartment ?? {}));

  constructor() {
    this.currentEmployee.resolve();
    effect(() => {
      const id = this.id();
      if (id) untracked(() => this.load(id));
    });
  }

  protected load(id: string): void {
    this.runService.find(id).subscribe({
      // Keep validation errors from the last action: the GET does not carry them.
      next: run =>
        this.run.update(prev => ({ ...run, validationErrors: prev?.id === run.id ? prev.validationErrors : run.validationErrors })),
    });
    this.summary.set({ data: null, loading: true, error: null });
    this.runService.summary(id).subscribe({
      next: data => this.summary.set({ data, loading: false, error: null }),
      error: (err: unknown) => this.summary.set({ data: null, loading: false, error: normalizeHttpError(err) }),
    });
    this.results.set({ data: null, loading: true, error: null });
    this.runService.results(id).subscribe({
      next: data => this.results.set({ data, loading: false, error: null }),
      error: (err: unknown) => this.results.set({ data: null, loading: false, error: normalizeHttpError(err) }),
    });
  }

  protected calculate(): void {
    this.act(this.runService.calculate(this.id()), 'humano.payrollRun.calculated');
  }

  protected recalculate(): void {
    this.act(this.runService.recalculate(this.id(), { payrollRunId: this.id(), recalculateAll: true }), 'humano.payrollRun.recalculated');
  }

  protected generatePayslips(): void {
    this.busy.set(true);
    this.runService.generatePayslips(this.id()).subscribe({
      next: () => {
        this.toast.success(this.translate.instant('humano.payrollRun.payslipsGenerated'));
        this.busy.set(false);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.busy.set(false);
      },
    });
  }

  protected confirmAction(): void {
    const kind = this.confirm();
    if (kind === 'approve') {
      const approverId = this.currentEmployee.currentEmployeeId();
      if (!approverId) {
        this.toast.danger(this.translate.instant('humano.payrollRun.noApproverProfile'));
        this.confirm.set(null);
        return;
      }
      this.act(this.runService.approve(this.id(), { payrollRunId: this.id(), approverId }), 'humano.payrollRun.approved');
    } else if (kind === 'post') {
      this.act(this.runService.postRun(this.id()), 'humano.payrollRun.posted');
    }
    this.confirm.set(null);
  }

  private act(call: Observable<PayrollRun>, successKey: string): void {
    this.busy.set(true);
    call.subscribe({
      next: run => {
        this.run.set(run);
        this.toast.success(stripHtml(this.translate.instant(successKey)));
        this.busy.set(false);
        this.load(this.id()); // refresh summary/results after a state change
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.busy.set(false);
      },
    });
  }
}
