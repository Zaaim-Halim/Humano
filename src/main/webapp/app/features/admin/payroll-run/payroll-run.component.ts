import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MoneyPipe, PayPeriodPipe } from 'app/shared/util/format.pipes';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
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
  AvatarComponent,
  InputComponent,
  SelectComponent,
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
 * Payroll Run — `/payroll/runs/:id`. Figures with change vs the previous posted run, where the
 * money goes (net / employee deductions / employer charges), earnings and deductions by
 * component, gross and net by department, and every employee's result (searchable, sortable,
 * with a net-vs-deductions bar), plus the lifecycle stepper and guarded approve/post.
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
    FormsModule,
    DecimalPipe,
    LucideAngularModule,
    AvatarComponent,
    InputComponent,
    SelectComponent,
    MoneyPipe,
    PayPeriodPipe,
    TranslatePipe,
    PageHeaderComponent,
    StepperComponent,
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
  /** Departments, largest gross first; bar widths relative to the largest gross. */
  protected readonly byDepartment = computed(() => {
    const list = Object.values(this.summary().data?.byDepartment ?? {}).sort((a, b) => b.totalGross - a.totalGross);
    const max = Math.max(1, ...list.map(d => d.totalGross));
    return list.map(d => ({
      name: d.departmentName,
      employeeCount: d.employeeCount,
      totalGross: d.totalGross,
      totalNet: d.totalNet,
      averageSalary: d.averageSalary,
      grossPct: (d.totalGross / max) * 100,
      netPct: (d.totalNet / max) * 100,
    }));
  });

  /** Total cost split: what employees take home, what is withheld, what the employer adds. */
  protected readonly costParts = computed(() => {
    const s = this.summary().data;
    if (!s) return [];
    const total = Math.max(1, s.totalEmployerCost);
    const parts = [
      { key: 'humano.payrollRun.net', amount: s.totalNet, color: 'var(--brand)' },
      { key: 'humano.payrollRun.employeeDeductions', amount: s.totalDeductions, color: 'var(--warning-solid)' },
      {
        key: 'humano.payrollRun.employerChargesShort',
        amount: Math.max(0, s.totalEmployerCost - s.totalGross),
        color: 'var(--violet-500)',
      },
    ];
    return parts.map(p => ({ ...p, pct: (p.amount / total) * 100 }));
  });

  protected readonly earnings = computed(() => this.components(this.summary().data?.earningsByComponent));
  protected readonly deductionParts = computed(() => this.components(this.summary().data?.deductionsByComponent));

  // Results list
  protected readonly query = signal('');
  protected readonly sortBy = signal<'net' | 'gross' | 'name'>('net');
  protected readonly sortOptions = computed(() => [
    { value: 'net', label: String(this.translate.instant('humano.payrollRun.sortNet')) },
    { value: 'gross', label: String(this.translate.instant('humano.payrollRun.sortGross')) },
    { value: 'name', label: String(this.translate.instant('humano.payrollRun.sortName')) },
  ]);
  protected readonly visibleResults = computed(() => {
    const q = this.query().trim().toLowerCase();
    const sort = this.sortBy();
    return (this.results().data ?? [])
      .filter(r => !q || (r.employeeName ?? '').toLowerCase().includes(q) || (r.employeeCode ?? '').toLowerCase().includes(q))
      .sort((a, b) => (sort === 'name' ? (a.employeeName ?? '').localeCompare(b.employeeName ?? '') : Number(b[sort]) - Number(a[sort])));
  });
  protected readonly maxGross = computed(() => Math.max(1, ...(this.results().data ?? []).map(r => Number(r.gross))));

  protected pct(value: number | null | undefined): string {
    const n = Number(value ?? 0);
    return `${n > 0 ? '+' : ''}${n.toFixed(1)}%`;
  }

  protected share(part: number, whole: number): string {
    return whole ? ((Number(part) / Number(whole)) * 100).toFixed(0) : '0';
  }

  private components(map: Record<string, number> | undefined): { name: string; amount: number; pct: number }[] {
    const list = Object.entries(map ?? {})
      .map(([name, amount]) => ({ name, amount: Number(amount) }))
      .sort((a, b) => b.amount - a.amount);
    const max = Math.max(1, ...list.map(c => c.amount));
    return list.map(c => ({ ...c, pct: (c.amount / max) * 100 }));
  }

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
