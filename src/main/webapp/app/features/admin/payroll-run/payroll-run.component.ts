import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
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
 * Backend reality (consume, don't invent):
 *  - There is **no GET for the run itself** — only `summary` + `results` (both
 *    GET). `RunStatus` is returned only by the action POSTs, so the stepper is
 *    driven by actions taken this session (TODO when a run-state endpoint lands).
 *  - No `validation-warnings`/`line-items` endpoints; `summary.errorCount` is the
 *    count, detailed `validationErrors` arrive on action responses.
 *  - `approve` needs an `approverId` UUID; the `Account` model omits `id` (it's
 *    on `MeResponse`), read defensively below — TODO: surface `id` on Account.
 */
@Component({
  selector: 'hum-payroll-run',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = this.accountService.trackCurrentAccount();

  protected readonly summary = signal<Holder<PayrollRunSummary>>(idle());
  protected readonly results = signal<Holder<PayrollResult[]>>(idle());
  /** Populated from lifecycle action responses (no GET for the run). */
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
    effect(() => {
      const id = this.id();
      if (id) untracked(() => this.load(id));
    });
  }

  protected load(id: string): void {
    this.run.set(null);
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
      // id from MeResponse; Account model omits it — read defensively (TODO: type `id` on Account).
      const approverId = (this.account() as unknown as { id?: string } | null)?.id;
      if (!approverId) {
        this.toast.danger('Approver id unavailable'); // TODO: backend/model — expose current-user id on Account.
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
