import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import {
  AlertComponent,
  ButtonComponent,
  PageHeaderComponent,
  SkeletonComponent,
  ToastService,
  TrendChartComponent,
  TrendPoint,
} from 'app/shared/ui';
import { downloadCsv } from 'app/shared/util/csv';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { PlatformOverview } from '../../models/platform.model';
import { PlatformBillingService } from '../../services/platform-billing.service';
import { PlatformConsoleService } from '../../services/platform-console.service';
import { TenantService } from '../../services/tenant.service';

/** Colours for lifecycle statuses (customers and subscriptions). */
export const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'var(--success-solid)',
  TRIAL: 'var(--info-solid, #3b82f6)',
  PAST_DUE: 'var(--warning-solid)',
  PENDING_PAYMENT: 'var(--warning-solid)',
  PENDING_SETUP: 'var(--neutral-400, #9ca3af)',
  PROVISIONING: 'var(--info-solid, #3b82f6)',
  SUSPENDED: 'var(--danger-solid)',
  PROVISIONING_FAILED: 'var(--danger-solid)',
  MIGRATION_FAILED: 'var(--danger-solid)',
  CANCELLED: 'var(--neutral-400, #9ca3af)',
  EXPIRED: 'var(--neutral-400, #9ca3af)',
  DEACTIVATED: 'var(--neutral-400, #9ca3af)',
  DELETED: 'var(--neutral-500, #6b7280)',
};

const PLAN_COLORS = ['var(--brand)', '#8b5cf6', '#06b6d4', '#f59e0b', '#10b981', '#ef4444'];

/**
 * Owner console home: the business at a glance — recurring revenue (MRR, ARR, ARPA, churn) and
 * its 12-month growth, cash collected, outstanding and overdue invoices, plan mix, customer and
 * subscription health, the customers needing attention — and downloadable CSV reports.
 */
@Component({
  selector: 'hum-console-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    PageHeaderComponent,
    SkeletonComponent,
    TrendChartComponent,
    MoneyPipe,
  ],
  templateUrl: './overview.component.html',
})
export default class ConsoleOverviewComponent {
  private readonly console = inject(PlatformConsoleService);
  private readonly billing = inject(PlatformBillingService);
  private readonly tenants = inject(TenantService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService).trackCurrentAccount();

  protected readonly today = new Date();
  protected readonly data = signal<PlatformOverview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly exporting = signal<string | null>(null);

  protected readonly greeting = computed(() => {
    const h = this.today.getHours();
    const key = h < 12 ? 'morning' : h < 18 ? 'afternoon' : 'evening';
    return this.translate.instant(`humano.console.overview.greeting.${key}`, { name: this.account()?.firstName ?? '' }) as string;
  });

  /** Month-over-month MRR change, in percent (null without a previous month). */
  protected readonly mrrDelta = computed(() => {
    const s = this.data()?.revenue.mrrByMonth ?? [];
    if (s.length < 2) return null;
    const prev = s[s.length - 2].value;
    const cur = s[s.length - 1].value;
    return prev ? Math.round(((cur - prev) / prev) * 1000) / 10 : null;
  });

  protected readonly mrrPoints = computed<TrendPoint[]>(() =>
    (this.data()?.revenue.mrrByMonth ?? []).map(m => ({
      label: this.monthLabel(m.month),
      value: m.value,
      title: `${this.monthLabel(m.month)}: ${m.value}`,
    })),
  );

  protected readonly collected = computed(() => this.bars(this.data()?.revenue.collectedByMonth ?? []));
  protected readonly signups = computed(() => this.bars(this.data()?.customers.signupsByMonth ?? []));

  protected readonly plans = computed(() => {
    const plans = this.data()?.plans ?? [];
    const total = plans.reduce((s, p) => s + p.mrr, 0);
    const maxCustomers = Math.max(1, ...plans.map(p => p.customers));
    return plans.map((p, i) => ({
      ...p,
      color: PLAN_COLORS[i % PLAN_COLORS.length],
      share: total ? (p.mrr / total) * 100 : 0,
      barPct: (p.customers / maxCustomers) * 100,
    }));
  });

  /** Attention items grouped per customer (a late payer can be both past due and overdue). */
  protected readonly attention = computed(() => {
    const groups = new Map<
      string,
      { tenantId: string; tenantName: string; severe: boolean; reasons: { kind: string; detail: string | null }[] }
    >();
    for (const a of this.data()?.attention ?? []) {
      const g = groups.get(a.tenantId) ?? { tenantId: a.tenantId, tenantName: a.tenantName, severe: false, reasons: [] };
      g.reasons.push({ kind: a.kind, detail: a.detail });
      g.severe ||= a.kind.includes('FAILED') || a.kind.includes('SUSPENDED');
      groups.set(a.tenantId, g);
    }
    return [...groups.values()].sort((a, b) => Number(b.severe) - Number(a.severe));
  });

  protected readonly customerStatus = computed(() => this.distribution(this.data()?.customers.byStatus ?? {}));
  protected readonly subscriptionStatus = computed(() => this.distribution(this.data()?.subscriptions.byStatus ?? {}));

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.console.overview().subscribe({
      next: d => this.data.set(d),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  // ---- Reports (CSV)

  protected exportRevenue(): void {
    const d = this.data();
    if (!d) return;
    const rows = d.revenue.mrrByMonth.map((m, i) => [
      m.month,
      m.value,
      d.revenue.collectedByMonth[i]?.value ?? 0,
      d.customers.signupsByMonth[i]?.value ?? 0,
    ]);
    downloadCsv(`humano-revenue-${this.stamp()}`, ['Month', `MRR (${d.currency})`, `Collected (${d.currency})`, 'New customers'], rows);
  }

  protected exportPlans(): void {
    const d = this.data();
    if (!d) return;
    downloadCsv(
      `humano-plan-mix-${this.stamp()}`,
      ['Plan', 'Type', `Monthly price (${d.currency})`, 'Available', 'Customers', `MRR (${d.currency})`],
      d.plans.map(p => [p.name, p.type, p.monthlyPrice, p.active ? 'yes' : 'no', p.customers, p.mrr]),
    );
  }

  protected exportCustomers(): void {
    this.exporting.set('customers');
    forkJoin({ tenants: this.tenants.list(undefined, { page: 0, size: 2000 }), subs: this.billing.subscriptions() }).subscribe({
      next: ({ tenants, subs }) => {
        const byTenant = new Map(subs.content.map(s => [s.tenantId, s]));
        downloadCsv(
          `humano-customers-${this.stamp()}`,
          ['Customer', 'Workspace', 'Domain', 'Country', 'Status', 'Plan', 'Billing cycle', 'Subscription', 'Customer since'],
          tenants.content.map(t => {
            const s = byTenant.get(t.id);
            return [
              t.name,
              t.subdomain,
              t.domain,
              t.country,
              t.status,
              s?.subscriptionPlanName ?? t.subscriptionPlanName,
              s?.billingCycle,
              s?.status,
              t.createdDate?.slice(0, 10),
            ];
          }),
        );
        this.exporting.set(null);
      },
      error: (err: unknown) => this.exportFailed(err),
    });
  }

  protected exportInvoices(): void {
    this.exporting.set('invoices');
    this.billing.invoices().subscribe({
      next: page => {
        downloadCsv(
          `humano-invoices-${this.stamp()}`,
          ['Invoice', 'Customer', 'Issued', 'Due', 'Paid', 'Status', 'Amount', 'Tax', 'Total'],
          page.content.map(i => [
            i.invoiceNumber,
            i.tenantName,
            i.issueDate?.slice(0, 10),
            i.dueDate?.slice(0, 10),
            i.paidDate?.slice(0, 10),
            i.status,
            i.amount,
            i.taxAmount,
            i.totalAmount,
          ]),
        );
        this.exporting.set(null);
      },
      error: (err: unknown) => this.exportFailed(err),
    });
  }

  protected statusColor(status: string): string {
    return STATUS_COLORS[status] ?? 'var(--neutral-400, #9ca3af)';
  }

  private exportFailed(err: unknown): void {
    this.exporting.set(null);
    this.toast.danger(normalizeHttpError(err));
  }

  private bars(series: { month: string; value: number }[]): { label: string; month: string; value: number; pct: number }[] {
    const max = Math.max(1, ...series.map(m => m.value));
    return series.map(m => ({
      label: this.monthLabel(m.month, true),
      month: m.month,
      value: m.value,
      pct: Math.max(2, (m.value / max) * 100),
    }));
  }

  private distribution(map: Record<string, number>): { key: string; count: number; pct: number; color: string }[] {
    const total = Object.values(map).reduce((s, n) => s + n, 0);
    return Object.entries(map)
      .sort((a, b) => b[1] - a[1])
      .map(([key, count]) => ({ key, count, pct: total ? (count / total) * 100 : 0, color: this.statusColor(key) }));
  }

  private monthLabel(month: string, short = false): string {
    const [y, m] = month.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString(
      this.translate.currentLang || 'en',
      short ? { month: 'short' } : { month: 'short', year: '2-digit' },
    );
  }

  private stamp(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
