import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { Invoice, InvoiceService, MeBillingService, Subscription, SubscriptionPlan, SubscriptionPlanService } from 'app/features/platform';
import { AlertComponent, BadgeComponent, ButtonComponent, SkeletonRowComponent } from 'app/shared/ui';
import { MoneyPipe } from 'app/shared/util/format.pipes';

/**
 * Settings → Plan & billing: the company's Humano subscription (plan, price, cycle, status, next
 * renewal) and its invoices. Read-only: plan changes and payments go through Humano.
 */
@Component({
  selector: 'hum-billing-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, TranslatePipe, LucideAngularModule, AlertComponent, BadgeComponent, ButtonComponent, SkeletonRowComponent, MoneyPipe],
  templateUrl: './billing-settings.component.html',
})
export class BillingSettingsComponent {
  private readonly meBilling = inject(MeBillingService);
  private readonly plansApi = inject(SubscriptionPlanService);
  private readonly invoiceService = inject(InvoiceService);

  protected readonly subscription = signal<Subscription | null | undefined>(undefined);
  protected readonly plan = signal<SubscriptionPlan | null>(null);
  protected readonly invoices = signal<Invoice[] | null>(null);
  protected readonly error = signal<string | null>(null);

  /** The platform bills in one currency; read it from the invoices (USD until there is one). */
  protected readonly currency = computed(() => this.invoices()?.[0]?.currency ?? 'USD');
  protected readonly monthlyPrice = computed(() => Number(this.plan()?.basePrice ?? this.plan()?.price ?? 0));
  protected readonly cyclePrice = computed(() =>
    this.subscription()?.billingCycle === 'YEARLY' ? Math.round(this.monthlyPrice() * 12 * 0.85 * 100) / 100 : this.monthlyPrice(),
  );
  protected readonly openTotal = computed(() =>
    (this.invoices() ?? [])
      .filter(i => i.status === 'PENDING' || i.status === 'OVERDUE')
      .reduce((s, i) => s + Number(i.totalAmount ?? 0), 0),
  );
  protected readonly sortedInvoices = computed(() =>
    [...(this.invoices() ?? [])].sort((a, b) => (b.issueDate ?? '').localeCompare(a.issueDate ?? '')),
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.meBilling.currentSubscription().subscribe({
      next: sub => {
        this.subscription.set(sub);
        if (sub.subscriptionPlanId)
          this.plansApi.find(sub.subscriptionPlanId).subscribe({ next: p => this.plan.set(p), error: () => this.plan.set(null) });
      },
      // No subscription yet (404) is a normal state, not an error.
      error: () => this.subscription.set(null),
    });
    this.invoiceService.list().subscribe({
      next: list => this.invoices.set(list),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.invoices.set([]);
      },
    });
  }
}
