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
  PLAN_COUNTERS,
  PLAN_MODULES,
  PlanFeature,
  StorageAddon,
  Tenant,
} from 'app/features/platform';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  IconButtonComponent,
  ProgressComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';
import { SubscriptionStatus } from 'app/features/platform/models/enums/subscription-status.enum';
import { MoneyPipe } from 'app/shared/util/format.pipes';
import { formatPlanAmount } from 'app/shared/util/plan-units';

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
    ProgressComponent,
    IconButtonComponent,
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
  /** Extra storage bought on top of the plan; null until loaded or when unavailable. */
  protected readonly storageAddon = signal<StorageAddon | null>(null);
  /** Packs being chosen in the storage dialog; null when it is closed. */
  protected readonly storagePacks = signal<number | null>(null);
  protected readonly confirming = signal<{ kind: 'change'; option: PlanOption } | { kind: 'cancel' } | null>(null);

  protected readonly isDemo = computed(() => !!this.tenant()?.demo);

  /** What the plan includes and what is used of it (shared with the shell). */
  protected readonly entitlements = this.meBilling.entitlements;
  /** Usage meters for the counted features; near = 80% or more. */
  protected readonly counters = computed(() =>
    (this.entitlements()?.features ?? [])
      .filter(f => PLAN_COUNTERS.includes(f.code) && f.included)
      .map(f => {
        const used = f.used ?? 0;
        return {
          code: f.code,
          name: f.name,
          used,
          limit: f.limit,
          usedLabel: formatPlanAmount(used, f.unit).value,
          limitLabel:
            f.limit === null
              ? null
              : `${formatPlanAmount(f.limit, f.unit).value} ${f.unit ? formatPlanAmount(f.limit, f.unit).unit : ''}`.trim(),
          unitLabel: f.unit === 'GB' ? 'GB' : '',
          full: f.limit !== null && used >= f.limit,
          near: f.limit !== null && used >= f.limit * 0.8,
        };
      }),
  );
  /** The enforced modules, included or read-only. */
  protected readonly modules = computed(() =>
    PLAN_MODULES.map(code => this.entitlements()?.features.find(f => f.code === code)).filter((f): f is NonNullable<typeof f> => !!f),
  );
  protected readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_SUBSCRIPTIONS));
  protected readonly running = computed(() => {
    const s = this.subscription();
    return !!s && RUNNING.includes(s.status);
  });
  /** In a free trial that no plan was chosen for yet (it would end read-only). */
  protected readonly trialOpen = computed(() => {
    const s = this.subscription();
    return s?.status === SubscriptionStatus.TRIAL && !s.autoRenew;
  });
  /** In a free trial with a plan chosen: the paid plan starts when the trial ends. */
  protected readonly trialContinues = computed(() => {
    const s = this.subscription();
    return s?.status === SubscriptionStatus.TRIAL && !!s.autoRenew;
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
              : this.t('humano.settings.billing.upTo', {
                  n: formatPlanAmount(it.limitValue, f.unit).value,
                  unit: f.unit ? formatPlanAmount(it.limitValue, f.unit).unit : unit,
                });
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
    // A plan change changes what is included: refresh it for this screen and the shell.
    this.meBilling.loadEntitlements();
    this.meBilling.storageAddon().subscribe({ next: a => this.storageAddon.set(a), error: () => this.storageAddon.set(null) });
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

  /**
   * What would stop working on the plan `planId` compared to today: counted features above its limit
   * (nothing more can be added) and modules it does not include (they become read-only).
   */
  protected downgradeWarnings(planId: string): string[] {
    const target = this.pricing()?.plans.find(p => p.id === planId);
    const current = this.entitlements();
    if (!target || !current) return [];
    const warnings: string[] = [];
    for (const f of current.features) {
      const item = target.items.find(i => i.featureId === f.featureId);
      const includedThere = item ? item.included : false;
      if (PLAN_COUNTERS.includes(f.code) && f.used !== null && includedThere && item?.limitValue != null && f.used > item.limitValue) {
        warnings.push(
          this.t('humano.plan.downgradeOver', { used: f.used, unit: f.unit ?? f.name, plan: target.name, limit: item.limitValue }),
        );
      } else if (PLAN_MODULES.includes(f.code) && f.included && !includedThere) {
        warnings.push(this.t('humano.plan.downgradeModule', { name: f.name, plan: target.name }));
      }
    }
    const addon = this.storageAddon();
    if (addon && addon.packs > 0 && !addon.availableOnPlanTypes.includes(target.type)) {
      warnings.push(this.t('humano.plan.downgradeStorageAddon', { packs: addon.packs, size: addon.packGb, plan: target.name }));
    }
    return warnings;
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

  /** The storage limit with `packs` packs instead of today's (null = unlimited). */
  protected storageLimitWith(packs: number): number | null {
    const a = this.storageAddon();
    const limit = this.entitlements()?.features.find(f => f.code === PlanFeature.STORAGE)?.limit ?? null;
    if (limit === null || !a) return limit;
    return limit + (packs - a.packs) * a.packGb;
  }

  protected storageLimitLabel(packs: number): string {
    const limit = this.storageLimitWith(packs);
    if (limit === null) return '';
    const amount = formatPlanAmount(limit, 'GB');
    return `${amount.value} ${amount.unit}`;
  }

  protected openStorage(): void {
    this.storagePacks.set(Math.max(1, this.storageAddon()?.packs ?? 0));
  }

  protected stepPacks(delta: number): void {
    this.storagePacks.update(n => Math.max(0, Math.min(1000, (n ?? 0) + delta)));
  }

  protected saveStorage(): void {
    const packs = this.storagePacks();
    if (packs === null) return;
    this.busy.set(true);
    this.meBilling.setStorageAddon(packs).subscribe({
      next: a => {
        this.busy.set(false);
        this.storagePacks.set(null);
        this.storageAddon.set(a);
        this.meBilling.loadEntitlements();
        this.toast.success(this.t(packs > 0 ? 'humano.plan.storageSaved' : 'humano.plan.storageRemoved'));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
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
