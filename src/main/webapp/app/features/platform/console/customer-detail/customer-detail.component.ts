import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, catchError, forkJoin, of } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  IconButtonComponent,
  DialogComponent,
  DrawerComponent,
  FormFieldComponent,
  InputComponent,
  ProgressComponent,
  SelectComponent,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';
import { saveFile } from 'app/shared/util/download';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { CustomerStorage } from '../../models/entitlements.model';
import { BillingCycle } from '../../models/enums/billing-cycle.enum';
import { Invoice } from '../../models/invoice.model';
import { SubscriptionPlan } from '../../models/subscription-plan.model';
import { Subscription } from '../../models/subscription.model';
import { TenantDetail } from '../../models/tenant.model';
import { PlatformBillingService } from '../../services/platform-billing.service';
import { PlatformConsoleService } from '../../services/platform-console.service';
import { SubscriptionPlanService } from '../../services/subscription-plan.service';
import { TenantService } from '../../services/tenant.service';
import { monthlyValue } from '../customers/customers.component';

type InvoiceAction = 'pay' | 'overdue' | 'void';

/**
 * One customer, from the owner's side: who they are, what they pay (plan, MRR, lifetime revenue,
 * outstanding), their subscription (change plan or cycle, cancel), their invoices (mark paid,
 * overdue, void), the workspace (provisioning, connection pool) and lifecycle — suspend or
 * reactivate, and deprovision behind a typed confirmation.
 */
@Component({
  selector: 'hum-console-customer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    IconButtonComponent,
    DialogComponent,
    DrawerComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SkeletonRowComponent,
    ProgressComponent,
    MoneyPipe,
  ],
  templateUrl: './customer-detail.component.html',
})
export default class ConsoleCustomerDetailComponent {
  readonly id = input.required<string>();

  private readonly tenants = inject(TenantService);
  private readonly billing = inject(PlatformBillingService);
  private readonly plansApi = inject(SubscriptionPlanService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly accountService = inject(AccountService);
  /** The invoice whose PDF is being fetched. */
  protected readonly downloadingInvoice = signal<string | null>(null);

  protected readonly currency = inject(PlatformConsoleService).currency;

  protected readonly detail = signal<TenantDetail | null>(null);
  protected readonly subscription = signal<Subscription | null>(null);
  protected readonly invoices = signal<Invoice[]>([]);
  protected readonly plans = signal<SubscriptionPlan[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly canSuspend = computed(() => this.can(Permission.SUSPEND_TENANT));
  protected readonly canDeprovision = computed(() => this.can(Permission.DEPROVISION_TENANT));
  protected readonly canManageBilling = computed(() => this.can(Permission.MANAGE_PLATFORM_BILLING));

  protected readonly tenant = computed(() => this.detail()?.tenant ?? null);
  protected readonly mrr = computed(() => monthlyValue(this.subscription() ?? undefined, new Map(this.plans().map(p => [p.id, p]))));
  protected readonly lifetime = computed(() =>
    this.invoices()
      .filter(i => i.status === 'PAID')
      .reduce((s, i) => s + Number(i.totalAmount ?? 0), 0),
  );
  protected readonly openInvoices = computed(() => this.invoices().filter(i => i.status === 'PENDING' || i.status === 'OVERDUE'));
  protected readonly outstanding = computed(() => this.openInvoices().reduce((s, i) => s + Number(i.totalAmount ?? 0), 0));
  protected readonly sortedInvoices = computed(() =>
    [...this.invoices()].sort((a, b) => (b.issueDate ?? '').localeCompare(a.issueDate ?? '')),
  );
  protected readonly poolEntries = computed(() => Object.entries(this.detail()?.poolStats ?? {}));

  // Dialogs / drawers.
  protected readonly confirmSuspend = signal(false);
  protected readonly confirmDeprovision = signal(false);
  protected readonly deprovisionText = signal('');
  protected readonly changePlanOpen = signal(false);
  protected readonly draftPlan = signal('');
  protected readonly draftCycle = signal<string>(BillingCycle.MONTHLY);
  protected readonly confirmCancel = signal(false);
  protected readonly invoiceAction = signal<{ invoice: Invoice; action: InvoiceAction } | null>(null);

  // Storage: extra packs bought on top of the plan.
  protected readonly storage = signal<CustomerStorage | null>(null);
  /** The storage editor's draft; null when closed. */
  protected readonly storageDraft = signal<{ packs: number; price: number } | null>(null);
  protected readonly storagePct = computed(() => {
    const s = this.storage();
    return s?.usedGb != null && s.limitGb ? Math.min(100, (s.usedGb / s.limitGb) * 100) : 0;
  });

  protected readonly planOptions = computed(() =>
    this.plans()
      .filter(p => p.active || p.id === this.subscription()?.subscriptionPlanId)
      .map(p => ({ value: p.id, label: `${p.displayName ?? p.subscriptionType} — ${this.formatMoney(p.basePrice ?? p.price)}/mo` })),
  );
  protected readonly cycleOptions = computed(() =>
    Object.values(BillingCycle).map(c => ({ value: c, label: this.t(`humano.console.cycle.${c}`) })),
  );

  constructor() {
    inject(PlatformConsoleService).ensureCurrency();
    effect(() => this.load(this.id()));
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    forkJoin({
      detail: this.tenants.find(id),
      sub: this.billing.subscriptionByTenant(id).pipe(catchError(() => of(null))) as Observable<Subscription | null>,
      invoices: this.billing.invoicesByTenant(id).pipe(catchError(() => of([] as Invoice[]))),
      plans: this.plansApi.query({ page: 0, size: 200 }),
      storage: this.billing.customerStorage(id).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ detail, sub, invoices, plans, storage }) => {
        this.storage.set(storage);
        this.detail.set(detail);
        this.subscription.set(sub);
        this.invoices.set(invoices);
        this.plans.set(plans.content);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected countryName(code: string | null): string {
    if (!code) return '';
    try {
      return new Intl.DisplayNames([this.translate.currentLang || 'en'], { type: 'region' }).of(code) ?? code;
    } catch {
      return code;
    }
  }

  // ---- Storage

  protected openStorage(): void {
    const a = this.storage()?.addon;
    if (!a) return;
    this.storageDraft.set({ packs: Math.max(1, a.packs), price: a.packMonthlyPrice ?? a.offerMonthlyPrice });
  }

  /** Number inputs hand over strings: kept as numbers from here on. */
  protected patchStorage(field: 'packs' | 'price', value: unknown): void {
    this.storageDraft.update(d => (d ? { ...d, [field]: Number(value) || 0 } : d));
  }

  protected saveStorage(): void {
    const t = this.tenant();
    const d = this.storageDraft();
    if (!t || !d) return;
    const packs = Math.max(0, Math.round(d.packs));
    this.run(this.billing.setCustomerStorage(t.id, packs, d.price), packs > 0 ? 'storageSet' : 'storageRemoved', () =>
      this.storageDraft.set(null),
    );
  }

  // ---- Lifecycle

  protected toggleSuspension(): void {
    const t = this.tenant();
    if (!t) return;
    const suspending = t.status !== 'SUSPENDED';
    this.run(suspending ? this.tenants.suspend(t.id) : this.tenants.activate(t.id), suspending ? 'suspended' : 'reactivated', () =>
      this.confirmSuspend.set(false),
    );
  }

  protected deprovision(): void {
    const t = this.tenant();
    if (!t || this.deprovisionText().trim() !== t.subdomain) return;
    this.run(this.tenants.deprovision(t.id), 'deprovisioned', () => {
      this.confirmDeprovision.set(false);
      this.deprovisionText.set('');
    });
  }

  // ---- Subscription

  protected openChangePlan(): void {
    const s = this.subscription();
    this.draftPlan.set(s?.subscriptionPlanId ?? this.plans()[0]?.id ?? '');
    this.draftCycle.set(s?.billingCycle ?? BillingCycle.MONTHLY);
    this.changePlanOpen.set(true);
  }

  protected savePlan(): void {
    const s = this.subscription();
    if (!s) return;
    this.run(
      this.billing.updateSubscription(s.id, { subscriptionPlanId: this.draftPlan(), billingCycle: this.draftCycle() as BillingCycle }),
      'planChanged',
      () => this.changePlanOpen.set(false),
    );
  }

  protected cancelSubscription(immediate: boolean): void {
    const s = this.subscription();
    if (!s) return;
    this.run(this.billing.cancelSubscription(s.id, immediate), immediate ? 'cancelledNow' : 'cancelledAtEnd', () =>
      this.confirmCancel.set(false),
    );
  }

  // ---- Invoices

  protected applyInvoiceAction(): void {
    const a = this.invoiceAction();
    if (!a) return;
    const call: Observable<unknown> =
      a.action === 'pay'
        ? this.billing.markInvoicePaid(a.invoice.id)
        : a.action === 'overdue'
          ? this.billing.markInvoiceOverdue(a.invoice.id)
          : this.billing.voidInvoice(a.invoice.id);
    this.run(call, `invoice.${a.action}`, () => this.invoiceAction.set(null));
  }

  /** Saves the invoice as a PDF document. */
  protected downloadInvoice(i: Invoice): void {
    this.downloadingInvoice.set(i.id);
    this.billing.invoicePdf(i.id).subscribe({
      next: blob => {
        this.downloadingInvoice.set(null);
        saveFile(blob, `${i.invoiceNumber}.pdf`);
      },
      error: () => {
        this.downloadingInvoice.set(null);
        this.toast.danger(this.t('humano.console.invoices.downloadFailed'));
      },
    });
  }

  private run(call: Observable<unknown>, doneKey: string, after: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        after();
        this.toast.success(this.t(`humano.console.customer.done.${doneKey}`));
        this.load();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  private can(permission: string): boolean {
    return !!this.accountService.trackCurrentAccount()()?.permissions?.includes(permission);
  }

  private formatMoney(v: number | null): string {
    return new Intl.NumberFormat(this.translate.currentLang || 'en', {
      style: 'currency',
      currency: this.currency(),
      maximumFractionDigits: 0,
    }).format(Number(v ?? 0));
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
