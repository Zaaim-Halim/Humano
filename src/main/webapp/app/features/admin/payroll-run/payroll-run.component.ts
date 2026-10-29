import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MoneyPipe, PayPeriodPipe, PluralPipe } from 'app/shared/util/format.pipes';
import { DatePipe, DecimalPipe, DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { IssueGroup, groupIssues } from './issue-groups';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { CurrentEmployeeService } from 'app/features/employee';
import {
  AlertComponent,
  ButtonComponent,
  CheckboxComponent,
  DialogComponent,
  EmptyStateComponent,
  FormFieldComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  AvatarComponent,
  InputComponent,
  MenuComponent,
  MenuItem,
  SelectComponent,
  StepperComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

import { ComponentTotal, PayrollPeriodService, PayrollResult, PayrollRun, PayrollRunService, PayrollRunSummary, RunStatus } from '../index';

const STEP_ORDER: RunStatus[] = [RunStatus.DRAFT, RunStatus.CALCULATED, RunStatus.APPROVED, RunStatus.POSTED];

interface Holder<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}
const idle = <T>(): Holder<T> => ({ data: null, loading: false, error: null });

/** The largest value per currency (at least 1), for bar widths that never compare currencies. */
function maxByCurrency<T extends { currencyCode: string | null }>(list: T[], value: (item: T) => number): (code: string | null) => number {
  const max = new Map<string | null, number>();
  for (const item of list) max.set(item.currencyCode, Math.max(max.get(item.currencyCode) ?? 1, value(item)));
  return code => max.get(code) ?? 1;
}

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
    NgTemplateOutlet,
    FormsModule,
    DecimalPipe,
    DatePipe,
    PluralPipe,
    FormFieldComponent,
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
    CheckboxComponent,
    TextareaComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    MenuComponent,
  ],
  templateUrl: './payroll-run.component.html',
})
export default class PayrollRunComponent {
  /** Run id from the route (`/payroll/runs/:id`). */
  readonly id = input.required<string>();

  /** Who may fill in employees' payroll template details (and so follow a refused employee's link). */
  protected readonly canEditTemplateDetails = inject(AccountService).hasPermission(Permission.MANAGE_TAX_BRACKETS);
  protected readonly canManageLeavePay = inject(AccountService).hasPermission(Permission.MANAGE_PAY_COMPONENTS);
  /** Who may move the period's payment date (the payroll-calendar endpoint). */
  private readonly canConfigureCalendar = inject(AccountService).hasPermission(Permission.CONFIGURE_PAYROLL_CALENDAR);

  private readonly runService = inject(PayrollRunService);
  private readonly periodService = inject(PayrollPeriodService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  protected readonly summary = signal<Holder<PayrollRunSummary>>(idle());
  protected readonly results = signal<Holder<PayrollResult[]>>(idle());
  /** The run itself — status, totals and (after a calculation) validation errors. */
  protected readonly run = signal<PayrollRun | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirm = signal<'approve' | 'post' | null>(null);

  // Export: any run past draft, as the register (CSV) or a workbook (Excel)
  protected readonly canExport = computed(() => {
    const status = this.run()?.status;
    return !!status && status !== RunStatus.DRAFT;
  });
  protected readonly exportOpen = signal(false);
  protected readonly exporting = signal(false);
  protected readonly exportItems = computed<MenuItem[]>(() => [
    { id: 'xlsx', icon: 'file-spreadsheet', label: String(this.translate.instant('humano.payrollRun.exportExcel')) },
    { id: 'csv', icon: 'file-text', label: String(this.translate.instant('humano.payrollRun.exportCsv')) },
  ]);

  /** Employees the latest calculation could not pay: approving leaves them unpaid, so it takes an override and a reason. */
  protected readonly unpaid = computed(() => (this.run()?.validationErrors ?? []).filter(v => v.severity !== 'WARNING'));
  protected readonly issueCount = computed(() => this.unpaid().length);
  /** Paid, but to check before approving: e.g. sick or unpaid leave paid in full because no leave pay rule covers it. */
  protected readonly warnings = computed(() => (this.run()?.validationErrors ?? []).filter(v => v.severity === 'WARNING'));
  /** Both lists by reason, so a reason shared by many employees is said once with its employees listed under it. */
  protected readonly unpaidGroups = computed(() => groupIssues(this.unpaid()));
  protected readonly warningGroups = computed(() => groupIssues(this.warnings()));
  /** Bullets shown per reason (three rows of three columns) and reasons shown per list until expanded. */
  protected readonly peopleShown = 9;
  protected readonly reasonsShown = 3;
  /** Reasons whose employees are all shown, and lists whose reasons are all shown. */
  protected readonly expandedReasons = signal<ReadonlySet<string>>(new Set());
  protected readonly expandedLists = signal<ReadonlySet<string>>(new Set());
  protected readonly approveDespiteIssues = signal(false);
  protected readonly overrideReason = signal('');
  protected readonly canConfirm = computed(
    () => this.confirm() !== 'approve' || this.issueCount() === 0 || (this.approveDespiteIssues() && this.overrideReason().trim() !== ''),
  );

  /** The payment date can move until the run is approved; a calculated run then needs recalculating. */
  protected readonly canChangePayDate = computed(() => {
    const status = this.run()?.status;
    return this.canConfigureCalendar && (status === RunStatus.DRAFT || status === RunStatus.CALCULATED);
  });
  protected readonly editingPayDate = signal(false);
  protected readonly payDateDraft = signal('');
  /** Paid in a later year than the period ends: templates that tax by payment date apply that year's rules. */
  protected readonly payYear = computed(() => {
    const pay = this.run()?.paymentDate?.slice(0, 4);
    const end = this.run()?.periodEnd?.slice(0, 4);
    return pay && end && pay !== end ? pay : null;
  });

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
  /** Employees are paid in several currencies and the run has no reporting currency: no single totals. */
  protected readonly mixedCurrencies = computed(() => (this.summary().data?.totalsByCurrency.length ?? 0) > 1);

  /**
   * Departments (split by currency in a mixed run), largest gross first within each currency; bar
   * widths relative to the largest gross in the same currency.
   */
  protected readonly byDepartment = computed(() => {
    const list = Object.entries(this.summary().data?.byDepartment ?? {})
      .map(([name, d]) => ({ ...d, name }))
      .sort((a, b) => (a.currencyCode ?? '').localeCompare(b.currencyCode ?? '') || b.totalGross - a.totalGross);
    const max = maxByCurrency(list, d => d.totalGross);
    return list.map(d => ({
      name: d.name,
      currencyCode: d.currencyCode,
      employeeCount: d.employeeCount,
      totalGross: d.totalGross,
      totalNet: d.totalNet,
      averageSalary: d.averageSalary,
      grossPct: (d.totalGross / max(d.currencyCode)) * 100,
      netPct: (d.totalNet / max(d.currencyCode)) * 100,
    }));
  });

  /** Total cost split per currency: what employees take home, what is withheld, what the employer adds. */
  protected readonly costSplits = computed(() =>
    (this.summary().data?.totalsByCurrency ?? []).map(t => {
      const total = Math.max(1, t.totalEmployerCost);
      const parts = [
        { key: 'humano.payrollRun.net', amount: t.totalNet, color: 'var(--brand)' },
        { key: 'humano.payrollRun.employeeDeductions', amount: t.totalDeductions, color: 'var(--warning-solid)' },
        {
          key: 'humano.payrollRun.employerChargesShort',
          amount: Math.max(0, t.totalEmployerCost - t.totalGross),
          color: 'var(--violet-500)',
        },
      ];
      return { currencyCode: t.currencyCode, parts: parts.map(p => ({ ...p, pct: (p.amount / total) * 100 })) };
    }),
  );

  protected readonly earnings = computed(() => this.components(this.summary().data?.earnings));
  protected readonly deductionParts = computed(() => this.components(this.summary().data?.deductions));

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

  constructor() {
    this.currentEmployee.resolve();
    effect(() => {
      const id = this.id();
      if (id) untracked(() => this.load(id));
    });
  }

  protected pct(value: number | null | undefined): string {
    const n = Number(value ?? 0);
    return `${n > 0 ? '+' : ''}${n.toFixed(1)}%`;
  }

  protected share(part: number, whole: number): string {
    return whole ? ((Number(part) / Number(whole)) * 100).toFixed(0) : '0';
  }

  protected load(id: string): void {
    this.runService.find(id).subscribe({ next: run => this.run.set(run) });
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

  protected exportRun(format: string | undefined): void {
    this.exportOpen.set(false);
    if (format !== 'csv' && format !== 'xlsx') return;
    this.exporting.set(true);
    this.runService.exportRun(this.id(), format).subscribe({
      next: res => {
        this.exporting.set(false);
        if (!res.body) return;
        const url = URL.createObjectURL(res.body);
        const a = this.document.createElement('a');
        a.href = url;
        a.download =
          /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ??
          `payroll-${this.run()?.periodCode ?? this.id()}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => {
        this.exporting.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected shownPeople(group: IssueGroup): IssueGroup['people'] {
    return this.expandedReasons().has(group.key) ? group.people : group.people.slice(0, this.peopleShown);
  }

  protected shownGroups(groups: IssueGroup[], list: string): IssueGroup[] {
    return this.expandedLists().has(list) ? groups : groups.slice(0, this.reasonsShown);
  }

  protected toggleReason(key: string): void {
    this.expandedReasons.update(keys => toggled(keys, key));
  }

  protected toggleList(list: string): void {
    this.expandedLists.update(keys => toggled(keys, list));
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

  protected openApprove(): void {
    this.approveDespiteIssues.set(false);
    this.overrideReason.set('');
    this.confirm.set('approve');
  }

  protected editPayDate(): void {
    this.payDateDraft.set(this.run()?.paymentDate ?? '');
    this.editingPayDate.set(true);
  }

  protected savePayDate(): void {
    const run = this.run();
    const date = this.payDateDraft();
    if (!run || !date) return;
    this.busy.set(true);
    this.periodService.updatePaymentDate(run.periodId, date).subscribe({
      next: () => {
        this.toast.success(
          this.translate.instant(
            run.status === RunStatus.CALCULATED ? 'humano.payrollRun.payDateSavedRecalculate' : 'humano.payrollRun.payDateSaved',
          ),
        );
        this.editingPayDate.set(false);
        this.busy.set(false);
        this.load(this.id());
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
      const override = this.issueCount() > 0 ? { approveDespiteIssues: true, issueOverrideReason: this.overrideReason().trim() } : {};
      this.act(this.runService.approve(this.id(), { payrollRunId: this.id(), approverId, ...override }), 'humano.payrollRun.approved');
    } else if (kind === 'post') {
      this.act(this.runService.postRun(this.id()), 'humano.payrollRun.posted');
    }
    this.confirm.set(null);
  }

  /** Components largest first within each currency; bar widths relative to the largest in the same currency. */
  private components(totals: ComponentTotal[] | undefined): { name: string; currencyCode: string; amount: number; pct: number }[] {
    const list = (totals ?? [])
      .map(c => ({ name: c.name, currencyCode: c.currencyCode, amount: c.amount }))
      .sort((a, b) => a.currencyCode.localeCompare(b.currencyCode) || b.amount - a.amount);
    const max = maxByCurrency(list, c => c.amount);
    return list.map(c => ({ ...c, pct: (c.amount / max(c.currencyCode)) * 100 }));
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

function toggled(keys: ReadonlySet<string>, key: string): ReadonlySet<string> {
  const next = new Set(keys);
  if (!next.delete(key)) next.add(key);
  return next;
}
