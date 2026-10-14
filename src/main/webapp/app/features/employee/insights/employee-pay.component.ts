import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { SalaryHistory } from 'app/features/admin/models/compensation.model';
import { Basis } from 'app/features/admin/models/enums/basis.enum';
import { Payslip } from 'app/features/admin/models/payslip.model';
import { CompensationService } from 'app/features/admin/services/compensation.service';
import { PayslipService } from 'app/features/admin/services/payslip.service';
import { AlertComponent, ButtonComponent, EmptyStateComponent, SkeletonRowComponent, TrendChartComponent, TrendPoint } from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { MeWorkService } from '../services/me-work.service';

/** Multiplier from a pay basis to a yearly amount (hourly assumes 40 h × 52 weeks). */
const PER_YEAR: Record<string, number> = { [Basis.MONTHLY]: 12, [Basis.ANNUAL]: 1, [Basis.HOURLY]: 2080 };

/**
 * Pay & compensation for one employee.
 *
 * - Headline: current base pay with its yearly equivalent, growth since the first recorded
 *   salary, the last change, and year-to-date net pay.
 * - Salary progression: a step chart of every base-pay change, plus the list of changes.
 * - Recent payslips: gross split into net and deductions per period (bars link to the payslip).
 *
 * Loads `GET /compensations/employees/{id}/history` and `GET /payslips/employees/{id}`; payslips
 * are optional (hidden if the viewer may not read them). Bump `reloadKey` to refresh, e.g.
 * after the salary editor saves; `loaded` reports the history (the editor uses it).
 */
@Component({
  selector: 'hum-employee-pay',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    MoneyPipe,
    AlertComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    TrendChartComponent,
  ],
  templateUrl: './employee-pay.component.html',
})
export class EmployeePayComponent {
  readonly employeeId = input.required<string>();
  /** Shows the "Adjust salary" / "Set up salary" button. */
  readonly canManage = input(false);
  readonly reloadKey = input(0);
  /** Read the signed-in employee's own data via `/api/me/**` (My profile). */
  readonly self = input(false);
  readonly edit = output<void>();
  readonly loaded = output<SalaryHistory>();

  private readonly compensationService = inject(CompensationService);
  private readonly payslipService = inject(PayslipService);
  private readonly meWork = inject(MeWorkService);
  private readonly translate = inject(TranslateService);

  protected readonly history = signal<SalaryHistory | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly payslips = signal<Payslip[]>([]);

  protected readonly current = computed(() => this.history()?.currentCompensation ?? null);
  protected readonly currency = computed(() => this.current()?.currencyCode ?? this.payslips()[0]?.currencyCode ?? 'USD');
  protected readonly yearly = computed(() => {
    const c = this.current();
    return c ? Number(c.baseAmount) * (PER_YEAR[c.basis] ?? 12) : null;
  });
  /** Newest change first. */
  protected readonly changes = computed(() => [...(this.history()?.history ?? [])].reverse());
  protected readonly lastChange = computed(() => this.changes()[0] ?? null);

  /** Step chart: the first recorded salary, then each change. */
  protected readonly salaryPoints = computed<TrendPoint[]>(() => {
    const h = this.history();
    const cur = this.current();
    if (!h || !cur) return [];
    const changes = h.history ?? [];
    const first = changes[0];
    const points: TrendPoint[] = [];
    const startAmount = first ? Number(first.previousAmount ?? first.newAmount) : Number(cur.baseAmount);
    const startDate = first ? null : cur.effectiveFrom;
    points.push({
      label: startDate ? this.year(startDate) : this.t('humano.insights.pay.start'),
      value: startAmount,
      annotation: this.compact(startAmount),
    });
    for (const c of changes) {
      points.push({
        label: this.year(c.effectiveFrom),
        value: Number(c.newAmount),
        annotation: this.compact(Number(c.newAmount)),
        title: `${c.effectiveFrom}: ${this.compact(Number(c.newAmount))} (${this.pct(c.changePercentage)})`,
      });
    }
    return points;
  });

  /** Oldest → newest payslips, bar heights relative to the largest gross. */
  protected readonly payBars = computed(() => {
    const list = [...this.payslips()].reverse();
    const max = Math.max(1, ...list.map(p => Number(p.gross)));
    return list.map(p => ({
      payslip: p,
      net: (Number(p.net) / max) * 100,
      deductions: (Number(p.totalDeductions) / max) * 100,
      month: new Date(`${p.periodStart ?? p.paymentDate}T00:00:00`).toLocaleDateString(undefined, { month: 'short' }),
    }));
  });
  protected readonly ytd = computed(() => {
    const year = String(new Date().getFullYear());
    const list = this.payslips().filter(p => p.periodStart?.startsWith(year));
    return {
      count: list.length,
      net: list.reduce((s, p) => s + Number(p.net), 0),
      gross: list.reduce((s, p) => s + Number(p.gross), 0),
    };
  });

  constructor() {
    effect(() => {
      const id = this.employeeId();
      this.reloadKey();
      untracked(() => this.load(id));
    });
  }

  protected load(id = this.employeeId()): void {
    this.loading.set(true);
    this.error.set(null);
    const history$ = this.self() ? this.meWork.salaryHistory() : this.compensationService.history(id);
    const payslips$ = this.self()
      ? this.meWork.payslips({ page: 0, size: 60 })
      : this.payslipService.forEmployee(id, { page: 0, size: 60 });
    history$.subscribe({
      next: h => {
        this.history.set(h);
        this.loading.set(false);
        this.loaded.emit(h);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
    // No server sort (the period date lives on a joined entity): newest first, last 12, client-side.
    payslips$.subscribe({
      next: page =>
        this.payslips.set([...page.content].sort((a, b) => (b.periodStart ?? '').localeCompare(a.periodStart ?? '')).slice(0, 12)),
      error: () => this.payslips.set([]),
    });
  }

  /** Payslip page: the HR view, or the self-service one on My profile. */
  protected payslipLink(id: string): string[] {
    return [this.self() ? '/my-payslips' : '/payroll/payslips', id];
  }

  protected pct(value: number | null | undefined): string {
    if (value === null || value === undefined) return '—';
    const n = Number(value);
    return `${n > 0 ? '+' : ''}${n.toFixed(1)}%`;
  }

  protected basisLabel(basis: string | null | undefined): string {
    return basis ? this.t(`humano.insights.pay.basis.${basis}`) : '';
  }

  private compact(n: number): string {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: this.currency(),
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(n);
  }

  private year(iso: string): string {
    return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
