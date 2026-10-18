import { ChangeDetectionStrategy, Component, TemplateRef, computed, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  Column,
  DataTableComponent,
  EmptyStateComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  SortState,
} from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { BillingCycle } from '../../models/enums/billing-cycle.enum';
import { SubscriptionPlan } from '../../models/subscription-plan.model';
import { Subscription } from '../../models/subscription.model';
import { Tenant } from '../../models/tenant.model';
import { PlatformBillingService } from '../../services/platform-billing.service';
import { SubscriptionPlanService } from '../../services/subscription-plan.service';
import { TenantService } from '../../services/tenant.service';
import { PlatformConsoleService } from '../../services/platform-console.service';

/** What a subscription is worth per month (yearly billed at 12 × 85%, as on the server). */
export function monthlyValue(sub: Subscription | undefined, plans: Map<string, SubscriptionPlan>): number {
  if (!sub?.subscriptionPlanId || !['ACTIVE', 'PAST_DUE'].includes(sub.status)) return 0;
  const plan = plans.get(sub.subscriptionPlanId);
  const price = Number(plan?.basePrice ?? plan?.price ?? 0);
  const plan_ = sub.billingCycle === BillingCycle.YEARLY ? price * 0.85 : price;
  // Add-ons (extra storage) are billed monthly on top of the plan.
  return plan_ + (sub.addonsMonthly ?? 0);
}

interface CustomerRow extends Record<string, unknown> {
  id: string;
  name: string;
  subdomain: string;
  country: string;
  status: string;
  plan: string;
  cycle: string;
  subscriptionStatus: string;
  mrr: number;
  since: string;
}

type Filter = 'all' | 'active' | 'trial' | 'risk' | 'inactive';

/**
 * Owner console — every customer (tenant) with its plan, MRR, subscription and workspace status:
 * searchable, filterable by segment (active, trial, at risk, inactive) and sortable; a row opens
 * the customer.
 */
@Component({
  selector: 'hum-console-customers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    DataTableComponent,
    EmptyStateComponent,
    InputComponent,
    PageHeaderComponent,
    SkeletonRowComponent,
    MoneyPipe,
  ],
  templateUrl: './customers.component.html',
})
export default class ConsoleCustomersComponent {
  private readonly tenants = inject(TenantService);
  private readonly billing = inject(PlatformBillingService);
  private readonly plansApi = inject(SubscriptionPlanService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  protected readonly currency = inject(PlatformConsoleService).currency;

  protected readonly rows = signal<CustomerRow[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly filter = signal<Filter>('all');
  protected readonly sort = signal<SortState>({ key: 'mrr', dir: 'desc' });

  private readonly customerCell = viewChild<TemplateRef<{ $implicit: CustomerRow; value: unknown }>>('customerCell');
  private readonly statusCell = viewChild<TemplateRef<{ $implicit: CustomerRow; value: unknown }>>('statusCell');
  private readonly subCell = viewChild<TemplateRef<{ $implicit: CustomerRow; value: unknown }>>('subCell');
  private readonly moneyCell = viewChild<TemplateRef<{ $implicit: CustomerRow; value: unknown }>>('moneyCell');
  private readonly dateCell = viewChild<TemplateRef<{ $implicit: CustomerRow; value: unknown }>>('dateCell');
  private readonly planCell = viewChild<TemplateRef<{ $implicit: CustomerRow; value: unknown }>>('planCell');

  protected readonly columns = computed<Column[]>(() => [
    {
      key: 'name',
      label: this.t('humano.console.customers.col.customer'),
      sortable: true,
      cell: this.customerCell() as TemplateRef<never>,
    },
    { key: 'plan', label: this.t('humano.console.customers.col.plan'), sortable: true, cell: this.planCell() as TemplateRef<never> },
    {
      key: 'mrr',
      label: this.t('humano.console.customers.col.mrr'),
      sortable: true,
      numeric: true,
      cell: this.moneyCell() as TemplateRef<never>,
    },
    { key: 'subscriptionStatus', label: this.t('humano.console.customers.col.subscription'), cell: this.subCell() as TemplateRef<never> },
    { key: 'status', label: this.t('humano.console.customers.col.workspace'), cell: this.statusCell() as TemplateRef<never> },
    { key: 'since', label: this.t('humano.console.customers.col.since'), sortable: true, cell: this.dateCell() as TemplateRef<never> },
  ]);

  protected readonly counts = computed(() => {
    const all = this.rows() ?? [];
    return {
      all: all.length,
      active: all.filter(r => this.matches(r, 'active')).length,
      trial: all.filter(r => this.matches(r, 'trial')).length,
      risk: all.filter(r => this.matches(r, 'risk')).length,
      inactive: all.filter(r => this.matches(r, 'inactive')).length,
    };
  });
  protected readonly filters: Filter[] = ['all', 'active', 'trial', 'risk', 'inactive'];

  protected readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    const f = this.filter();
    const { key, dir } = this.sort();
    const list = (this.rows() ?? []).filter(
      r => this.matches(r, f) && (!q || `${r.name} ${r.subdomain} ${r.plan} ${r.country}`.toLowerCase().includes(q)),
    );
    const sign = dir === 'asc' ? 1 : -1;
    return [...list].sort((a, b) => {
      const x = a[key];
      const y = b[key];
      return (typeof x === 'number' && typeof y === 'number' ? x - y : String(x ?? '').localeCompare(String(y ?? ''))) * sign;
    });
  });

  protected readonly totalMrr = computed(() => this.visible().reduce((s, r) => s + r.mrr, 0));

  constructor() {
    inject(PlatformConsoleService).ensureCurrency();
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    forkJoin({
      tenants: this.tenants.list(undefined, { page: 0, size: 2000 }),
      subs: this.billing.subscriptions(),
      plans: this.plansApi.query({ page: 0, size: 200 }),
    }).subscribe({
      next: ({ tenants, subs, plans }) => {
        const planById = new Map(plans.content.map(p => [p.id, p]));
        // Latest subscription per customer.
        const subByTenant = new Map<string, Subscription>();
        for (const s of subs.content) {
          const cur = subByTenant.get(s.tenantId);
          if (!cur || (s.startDate ?? '') > (cur.startDate ?? '')) subByTenant.set(s.tenantId, s);
        }
        this.rows.set(tenants.content.map(t => this.toRow(t, subByTenant.get(t.id), planById)));
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected open(row: CustomerRow): void {
    void this.router.navigate(['/platform/customers', row.id]);
  }

  protected countryName(code: string): string {
    if (!code) return '';
    try {
      return new Intl.DisplayNames([this.translate.currentLang || 'en'], { type: 'region' }).of(code) ?? code;
    } catch {
      return code;
    }
  }

  private toRow(t: Tenant, sub: Subscription | undefined, plans: Map<string, SubscriptionPlan>): CustomerRow {
    return {
      id: t.id,
      name: t.name,
      subdomain: t.subdomain ?? '',
      country: t.country ?? '',
      status: t.status,
      plan: sub?.subscriptionPlanName ?? t.subscriptionPlanName ?? '',
      cycle: sub?.billingCycle ?? '',
      subscriptionStatus: sub?.status ?? '',
      mrr: Math.round(monthlyValue(sub, plans) * 100) / 100,
      since: t.createdDate ?? '',
    };
  }

  private matches(r: CustomerRow, f: Filter): boolean {
    switch (f) {
      case 'active':
        return r.status === 'ACTIVE' && r.subscriptionStatus === 'ACTIVE';
      case 'trial':
        return r.subscriptionStatus === 'TRIAL';
      case 'risk':
        return ['PAST_DUE', 'SUSPENDED', 'PENDING_PAYMENT'].includes(r.subscriptionStatus) || r.status.endsWith('FAILED');
      case 'inactive':
        return ['SUSPENDED', 'DEACTIVATED', 'DELETED'].includes(r.status) || ['CANCELLED', 'EXPIRED'].includes(r.subscriptionStatus);
      default:
        return true;
    }
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
