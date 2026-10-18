import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { PublicPricing } from 'app/features/marketing/marketing.service';
import {
  BillingCycle,
  CurrentTenantService,
  Invoice,
  InvoiceService,
  MeBillingService,
  Subscription,
  SubscriptionPlan,
  SubscriptionPlanService,
  Tenant,
} from 'app/features/platform';
import { AlertComponent, BadgeComponent, ButtonComponent, DialogComponent, SkeletonRowComponent, ToastService } from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

/** Yearly billing: 12 months at 85%, as the server bills it. */
const YEARLY_FACTOR = 0.85;
/** Subscription states in which the plan can still change in place. */
const RUNNING = ['TRIAL', 'ACTIVE', 'PAST_DUE', 'PENDING_PAYMENT'];

interface PlanOption {
  id: string;
  name: string;
  description: string | null;
  monthlyPrice: number;
  featured: boolean;
  highlights: string[];
}

/**
 * Settings → Plan & billing: the company's Humano subscription and its invoices. Billing admins
 * (`MANAGE_SUBSCRIPTIONS`) can upgrade or downgrade (effective now, new price from the next
 * invoice), switch monthly / yearly, cancel at the end of the period, take a cancellation back, or
 * pick a plan again once it has ended. A demo workspace has no subscription: it only shows when
 * the demo ends.
 */
@Component({
  selector: 'hum-billing-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    SkeletonRowComponent,
    MoneyPipe,
  ],
  templateUrl: './billing-settings.component.html',
})
export class BillingSettingsComponent {
  private readonly meBilling = inject(MeBillingService);
  private readonly plansApi = inject(SubscriptionPlanService);
  private readonly invoiceService = inject(InvoiceService);
  private readonly tenantApi = inject(CurrentTenantService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService).trackCurrentAccount();

  protected readonly tenant = signal<Tenant | null>(null);
  protected readonly subscription = signal<Subscription | null | undefined>(undefined);
  protected readonly plan = signal<SubscriptionPlan | null>(null);
  protected readonly invoices = signal<Invoice[] | null>(null);
  protected readonly pricing = signal<PublicPricing | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  /** The cycle the plan picker shows prices for (defaults to the current one). */
  protected readonly cycle = signal<BillingCycle>(BillingCycle.MONTHLY);
  protected readonly confirming = signal<{ kind: 'change'; option: PlanOption } | { kind: 'cancel' } | null>(null);

  protected readonly isDemo = computed(() => !!this.tenant()?.demo);
  protected readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_SUBSCRIPTIONS));
  protected readonly running = computed(() => {
    const s = this.subscription();
    return !!s && RUNNING.includes(s.status);
  });
  /** In a free trial that no plan was chosen for yet (it would end read-only). */
  protected readonly trialOpen = computed(() => {
    const s = this.subscription();
    return s?.status === 'TRIAL' && !s.autoRenew;
  });
  /** In a free trial with a plan chosen: the paid plan starts when the trial ends. */
  protected readonly trialContinues = computed(() => {
    const s = this.subscription();
    return s?.status === 'TRIAL' && !!s.autoRenew;
  });

  /** When a cancelled subscription stops: a trial at its trial end, otherwise at the period end. */
  protected readonly endDate = computed(() => {
    const s = this.subscription();
    return s?.status === 'TRIAL' && s.trialEnd ? s.trialEnd : (s?.currentPeriodEnd ?? null);
  });

  /** The platform bills in one currency. */
  protected readonly currency = computed(() => this.pricing()?.currency ?? this.invoices()?.[0]?.currency ?? 'USD');
  protected readonly monthlyPrice = computed(() => Number(this.plan()?.basePrice ?? this.plan()?.price ?? 0));
  protected readonly cyclePrice = computed(() =>
    this.subscription()?.billingCycle === 'YEARLY' ? Math.round(this.monthlyPrice() * 12 * YEARLY_FACTOR * 100) / 100 : this.monthlyPrice(),
  );
  protected readonly openTotal = computed(() =>
    (this.invoices() ?? [])
      .filter(i => i.status === 'PENDING' || i.status === 'OVERDUE')
      .reduce((s, i) => s + Number(i.totalAmount ?? 0), 0),
  );
  protected readonly sortedInvoices = computed(() =>
    [...(this.invoices() ?? [])].sort((a, b) => (b.issueDate ?? '').localeCompare(a.issueDate ?? '')),
  );

  /** The plans on sale, cheapest first, each with what it adds over the previous one. */
  protected readonly options = computed<PlanOption[]>(() => {
    const p = this.pricing();
    if (!p) return [];
    const names = new Map(p.features.map(f => [f.id, f]));
    return p.plans.map((plan, i) => {
      const prev = i > 0 ? p.plans[i - 1] : null;
      const before = new Map((prev?.items ?? []).filter(it => it.included).map(it => [it.featureId, `${it.limitValue}|${it.note}`]));
      const highlights = plan.items
        .filter(it => it.included && names.has(it.featureId) && before.get(it.featureId) !== `${it.limitValue}|${it.note}`)
        .slice(0, 4)
        .map(it => {
          const f = names.get(it.featureId)!;
          if (f.limitType === 'QUANTITY') {
            const unit = f.unit ?? f.name.toLowerCase();
            return it.limitValue === null
              ? this.t('humano.settings.billing.unlimited', { unit })
              : this.t('humano.settings.billing.upTo', { n: it.limitValue, unit });
          }
          return f.name;
        });
      return {
        id: plan.id,
        name: plan.name,
        description: plan.description,
        monthlyPrice: plan.monthlyPrice,
        featured: plan.featured,
        highlights,
      };
    });
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.tenantApi.current().subscribe({ next: t => this.tenant.set(t), error: () => this.tenant.set(null) });
    this.meBilling.currentSubscription().subscribe({
      next: sub => {
        this.subscription.set(sub);
        if (sub.billingCycle) this.cycle.set(sub.billingCycle);
        if (sub.subscriptionPlanId)
          this.plansApi.find(sub.subscriptionPlanId).subscribe({ next: p => this.plan.set(p), error: () => this.plan.set(null) });
      },
      // No subscription yet (404) is a normal state, not an error.
      error: () => this.subscription.set(null),
    });
    this.meBilling.plans().subscribe({ next: p => this.pricing.set(p), error: () => this.pricing.set(null) });
    this.invoiceService.list().subscribe({
      next: list => this.invoices.set(list),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.invoices.set([]);
      },
    });
  }

  protected price(o: PlanOption): number {
    return this.cycle() === BillingCycle.YEARLY ? Math.round(o.monthlyPrice * 12 * YEARLY_FACTOR * 100) / 100 : o.monthlyPrice;
  }

  /** What picking this plan means against the current subscription. */
  protected relation(o: PlanOption): 'current' | 'upgrade' | 'downgrade' | 'switch' | 'choose' {
    const s = this.subscription();
    if (!s || !this.running()) return 'choose';
    if (s.subscriptionPlanId === o.id) {
      // A free trial not yet set to continue: its own plan can be chosen to keep it after the trial.
      if (this.trialOpen()) return 'choose';
      return s.billingCycle === this.cycle() ? 'current' : 'switch';
    }
    return o.monthlyPrice > this.monthlyPrice() ? 'upgrade' : 'downgrade';
  }

  protected confirmChange(): void {
    const c = this.confirming();
    if (c?.kind !== 'change') return;
    this.run(this.meBilling.changePlan(c.option.id, this.cycle()), 'humano.settings.billing.changed');
  }

  protected confirmCancel(): void {
    this.run(this.meBilling.cancel(), 'humano.settings.billing.cancelled');
  }

  protected resume(): void {
    this.run(this.meBilling.resume(), 'humano.settings.billing.resumed');
  }

  private run(call: Observable<unknown>, doneKey: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.confirming.set(null);
        this.toast.success(this.t(doneKey));
        this.load();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
