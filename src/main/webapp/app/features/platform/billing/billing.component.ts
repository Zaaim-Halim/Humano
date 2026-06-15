import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import { ThemeService } from 'app/core/theme/theme.service';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  ProgressComponent,
  SelectComponent,
  SkeletonRowComponent,
} from 'app/shared/ui';

import { Invoice, InvoiceStatus, MeBillingService, Subscription, SubscriptionPlan, SubscriptionPlanService } from '../index';
import { InvoiceService } from '../index';

/**
 * Billing (Platform hero screen, self-serve) — `/api/billing/me`. Shows the
 * current subscription/plan, an invoices table (unpaged `List<Invoice>`), and
 * the platform violet chrome while mounted. Invoices are read-only here (the
 * markPaid/markOverdue/void actions on `InvoiceService` are superadmin
 * invoice-management, not part of this self-serve view).
 *
 * TODO: backend — several spec affordances have no endpoint and are surfaced as
 * present-but-disabled rather than faked:
 *   - seat usage (Progress) — no seats/usage endpoint
 *   - payment method — no `/api/me/billing/payment-method`
 *   - per-invoice PDF — no `/{id}/pdf`
 *   - currency code — no currency field on Invoice/Subscription/Plan, so amounts
 *     render as bare tabular figures (no hardcoded symbol per the no-fake rule)
 */
@Component({
  selector: 'hum-billing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    ProgressComponent,
    SelectComponent,
    BadgeComponent,
    ButtonComponent,
    AlertComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './billing.component.html',
})
export default class BillingComponent {
  private readonly fb = inject(FormBuilder);
  private readonly meBilling = inject(MeBillingService);
  private readonly plans = inject(SubscriptionPlanService);
  private readonly invoiceService = inject(InvoiceService);
  private readonly theme = inject(ThemeService);
  private readonly translate = inject(TranslateService);

  // Current subscription + the plan it points at (price lives on the plan).
  protected readonly subscription = signal<Subscription | null>(null);
  protected readonly plan = signal<SubscriptionPlan | null>(null);
  protected readonly subLoading = signal(false);
  protected readonly subError = signal<string | null>(null);

  // Invoices — unpaged list, client-side status filter.
  private readonly invoices = signal<Invoice[]>([]);
  protected readonly invLoading = signal(false);
  protected readonly invError = signal<string | null>(null);

  protected readonly filters = this.fb.nonNullable.group({ status: '' });
  private readonly statusFilter = signal<InvoiceStatus | ''>('');

  protected readonly statusOptions = computed(() => [
    { value: '', label: this.translate.instant('humano.billing.all') },
    ...Object.values(InvoiceStatus).map(s => ({ value: s, label: s })),
  ]);

  protected readonly visibleInvoices = computed(() => {
    const f = this.statusFilter();
    const all = this.invoices();
    return f ? all.filter(i => i.status === f) : all;
  });
  protected readonly invEmpty = computed(() => !this.invLoading() && !this.invError() && this.visibleInvoices().length === 0);
  /** Distinguishes "no invoices at all" (cold start) from "filter matched nothing". */
  protected readonly invFilteredOut = computed(() => this.statusFilter() !== '' && this.invoices().length > 0);

  protected clearFilters(): void {
    this.filters.reset({ status: '' });
  }

  protected readonly nextInvoiceDate = computed(() => this.subscription()?.currentPeriodEnd ?? null);

  constructor() {
    // Violet platform chrome while this surface is mounted (3.4).
    this.theme.setChrome('platform');
    inject(DestroyRef).onDestroy(() => this.theme.setChrome('app'));

    this.filters.valueChanges.pipe(takeUntilDestroyed()).subscribe(v => this.statusFilter.set((v.status as InvoiceStatus) || ''));

    this.loadSubscription();
    this.loadInvoices();
  }

  protected loadSubscription(): void {
    this.subLoading.set(true);
    this.subError.set(null);
    this.meBilling.currentSubscription().subscribe({
      next: sub => {
        this.subscription.set(sub);
        this.subLoading.set(false);
        if (sub.subscriptionPlanId) this.loadPlan(sub.subscriptionPlanId);
      },
      error: (err: unknown) => {
        this.subError.set(normalizeHttpError(err));
        this.subLoading.set(false);
      },
    });
  }

  private loadPlan(id: string): void {
    // Best-effort — price for the "current plan" card. A failure here must not
    // break the subscription view, so we swallow it (plan() stays null).
    this.plans.find(id).subscribe({ next: p => this.plan.set(p), error: () => this.plan.set(null) });
  }

  protected loadInvoices(): void {
    this.invLoading.set(true);
    this.invError.set(null);
    this.invoiceService.list().subscribe({
      next: rows => {
        this.invoices.set(rows);
        this.invLoading.set(false);
      },
      error: (err: unknown) => {
        this.invError.set(normalizeHttpError(err));
        this.invLoading.set(false);
      },
    });
  }
}
