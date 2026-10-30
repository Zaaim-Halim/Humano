import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, switchMap } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  IconButtonComponent,
  CheckboxComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonRowComponent,
  SwitchComponent,
  TabItem,
  TabsComponent,
  ToastService,
} from 'app/shared/ui';
import { saveFile } from 'app/shared/util/download';
import { MoneyPipe } from 'app/shared/util/format.pipes';

import { SubscriptionType } from '../../models/enums/subscription-type.enum';
import { Invoice } from '../../models/invoice.model';
import { Coupon, PlanBreakdown, PlanComparison, PlanFeatureCell, PlanFeatureDef } from '../../models/platform.model';
import { SubscriptionPlan } from '../../models/subscription-plan.model';
import { AddonRow } from '../../models/entitlements.model';
import { PlatformBillingService } from '../../services/platform-billing.service';
import { PlatformConsoleService } from '../../services/platform-console.service';
import { SubscriptionPlanService } from '../../services/subscription-plan.service';
import { InvoiceStatus } from '../../models/enums/invoice-status.enum';

type Tab = 'invoices' | 'plans' | 'coupons' | 'addons';

interface CouponDraft {
  code: string;
  type: 'PERCENT' | 'FIXED';
  value: string;
  expiry: string;
  max: string;
  /** '' for any billing cycle. */
  cycle: '' | 'MONTHLY' | 'YEARLY';
  /** Empty for any plan. */
  planIds: string[];
}

type PlanDraft = {
  id: string | null;
  name: string;
  type: string;
  price: string;
  description: string;
  featured: boolean;
  publicListed: boolean;
};
type InvoiceFilter = 'open' | 'overdue' | 'paid' | 'all';

/**
 * Owner console — revenue operations across all customers (`/platform/billing/:tab`):
 * invoices (filter, search, mark paid / overdue, void), the plan catalog (create, edit price,
 * retire) with how many customers each plan carries, and coupons (create, track redemptions,
 * deactivate, delete).
 */
@Component({
  selector: 'hum-console-billing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    IconButtonComponent,
    CheckboxComponent,
    DialogComponent,
    DrawerComponent,
    EmptyStateComponent,
    FormFieldComponent,
    InputComponent,
    PageHeaderComponent,
    SelectComponent,
    SkeletonRowComponent,
    SwitchComponent,
    TabsComponent,
    MoneyPipe,
  ],
  templateUrl: './console-billing.component.html',
})
export default class ConsoleBillingComponent {
  // eslint-disable-next-line @angular-eslint/no-input-rename -- bound to the ?tab= query parameter
  readonly tabParam = input<string | undefined>(undefined, { alias: 'tab' });

  private readonly billing = inject(PlatformBillingService);
  private readonly console = inject(PlatformConsoleService);
  private readonly plansApi = inject(SubscriptionPlanService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly account = inject(AccountService).trackCurrentAccount();
  /** The invoice whose PDF is being fetched. */
  protected readonly downloadingInvoice = signal<string | null>(null);

  protected readonly currency = this.console.currency;

  protected readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_PLATFORM_BILLING));
  protected readonly tab = computed<Tab>(() => {
    const t = this.tabParam();
    return t === 'plans' || t === 'coupons' || t === 'addons' ? t : 'invoices';
  });
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'invoices', label: this.t('humano.console.nav.invoices'), icon: 'receipt' },
    { id: 'plans', label: this.t('humano.console.nav.plans'), icon: 'layers' },
    { id: 'coupons', label: this.t('humano.console.nav.coupons'), icon: 'ticket' },
    { id: 'addons', label: this.t('humano.console.nav.addons'), icon: 'hard-drive' },
  ]);

  // ---- Add-ons
  protected readonly addons = signal<AddonRow[] | null>(null);
  protected readonly addonsError = signal<string | null>(null);
  /** Monthly add-on revenue from running subscriptions (trials are not billed). */
  protected readonly addonsMonthly = computed(() =>
    (this.addons() ?? [])
      .filter(a => a.subscriptionStatus === 'ACTIVE' || a.subscriptionStatus === 'PAST_DUE')
      .reduce((s, a) => s + a.monthlyTotal, 0),
  );
  protected readonly addonsGb = computed(() => (this.addons() ?? []).reduce((s, a) => s + a.quantity * a.unitSize, 0));
  protected readonly busy = signal(false);

  // ---- Invoices
  protected readonly invoices = signal<Invoice[] | null>(null);
  protected readonly invoicesError = signal<string | null>(null);
  protected readonly invoiceFilter = signal<InvoiceFilter>('open');
  protected readonly invoiceFilters: InvoiceFilter[] = ['open', 'overdue', 'paid', 'all'];
  protected readonly invoiceQuery = signal('');
  protected readonly invoiceAction = signal<{ invoice: Invoice; action: 'pay' | 'overdue' | 'void' } | null>(null);
  private readonly now = Date.now();

  protected readonly invoiceCounts = computed(() => {
    const all = this.invoices() ?? [];
    return {
      open: all.filter(i => this.isOpen(i)).length,
      overdue: all.filter(i => this.isLate(i)).length,
      paid: all.filter(i => i.status === InvoiceStatus.PAID).length,
      all: all.length,
    };
  });
  protected readonly invoiceTotals = computed(() => {
    const all = this.invoices() ?? [];
    const sum = (list: Invoice[]): number => list.reduce((s, i) => s + Number(i.totalAmount ?? 0), 0);
    const monthAgo = this.now - 30 * 86400000;
    return {
      open: sum(all.filter(i => this.isOpen(i))),
      overdue: sum(all.filter(i => this.isLate(i))),
      paid30: sum(all.filter(i => i.status === InvoiceStatus.PAID && i.paidDate && Date.parse(i.paidDate) > monthAgo)),
    };
  });
  protected readonly visibleInvoices = computed(() => {
    const q = this.invoiceQuery().trim().toLowerCase();
    const f = this.invoiceFilter();
    return (this.invoices() ?? []).filter(i => {
      const match =
        f === 'all' ||
        (f === 'open' && this.isOpen(i)) ||
        (f === 'overdue' && this.isLate(i)) ||
        (f === 'paid' && i.status === InvoiceStatus.PAID);
      return match && (!q || `${i.invoiceNumber} ${i.tenantName ?? ''}`.toLowerCase().includes(q));
    });
  });

  // ---- Plans
  protected readonly plans = signal<SubscriptionPlan[] | null>(null);
  protected readonly planStats = signal<Map<string, PlanBreakdown>>(new Map());
  protected readonly planDraft = signal<PlanDraft | null>(null);
  protected readonly typeOptions = computed(() =>
    Object.values(SubscriptionType).map(t => ({ value: t, label: this.t(`humano.console.planType.${t}`) })),
  );

  // ---- Plan features (comparison)
  protected readonly comparison = signal<PlanComparison | null>(null);
  /** Plans by price, each with its cells keyed by feature. */
  protected readonly planColumns = computed(() => {
    const cmp = this.comparison();
    const byPlan = new Map((cmp?.plans ?? []).map(p => [p.planId, new Map(p.items.map(i => [i.featureId, i]))]));
    return (this.plans() ?? []).map(plan => ({ plan, cells: byPlan.get(plan.id) ?? new Map<string, PlanFeatureCell>() }));
  });
  /** Catalog features grouped by category (catalog order). */
  protected readonly featureGroups = computed(() => {
    const groups = new Map<string, PlanFeatureDef[]>();
    for (const f of this.comparison()?.features ?? []) {
      const key = f.category ?? this.t('humano.console.plans.otherCategory');
      groups.set(key, [...(groups.get(key) ?? []), f]);
    }
    return [...groups.entries()].map(([category, features]) => ({ category, features }));
  });
  protected readonly categories = computed(() => this.featureGroups().map(g => g.category));
  /**
   * What each plan adds over the next cheaper one — the price difference, explained. The first
   * plan lists everything it includes.
   */
  protected readonly highlights = computed(() => {
    const cols = this.planColumns();
    const features = this.comparison()?.features ?? [];
    const result = new Map<string, { label: string; extra: boolean }[]>();
    cols.forEach((col, i) => {
      const prev = i > 0 ? cols[i - 1] : null;
      const items: { label: string; extra: boolean }[] = [];
      for (const f of features) {
        const cell = col.cells.get(f.id);
        if (!cell?.included) continue;
        const before = prev?.cells.get(f.id);
        if (prev && before?.included && this.rank(f, before) >= this.rank(f, cell) && (before.note ?? '') === (cell.note ?? '')) continue;
        items.push({ label: this.highlightLabel(f, cell), extra: !!prev });
      }
      result.set(col.plan.id, items);
    });
    return result;
  });
  protected readonly cheaperPlanName = computed(() => {
    const cols = this.planColumns();
    return new Map(cols.map((c, i) => [c.plan.id, i > 0 ? (cols[i - 1].plan.displayName ?? cols[i - 1].plan.subscriptionType) : null]));
  });
  protected readonly expanded = signal<Set<string>>(new Set());

  /** Draft of what the edited plan includes, keyed by feature. */
  protected readonly draftItems = signal<Record<string, { included: boolean; limit: string; note: string }>>({});
  protected readonly featureDraft = signal<{
    id: string | null;
    name: string;
    category: string;
    limitType: 'BOOLEAN' | 'QUANTITY';
    unit: string;
    description: string;
  } | null>(null);
  protected readonly featureDelete = signal<PlanFeatureDef | null>(null);
  protected readonly limitTypeOptions = computed(() => [
    { value: 'BOOLEAN', label: this.t('humano.console.features.kindBoolean') },
    { value: 'QUANTITY', label: this.t('humano.console.features.kindQuantity') },
  ]);

  // ---- Coupons
  protected readonly coupons = signal<Coupon[] | null>(null);
  protected readonly couponDraft = signal<CouponDraft | null>(null);
  protected readonly couponDelete = signal<Coupon | null>(null);
  protected readonly couponTypeOptions = computed(() => [
    { value: 'PERCENT', label: this.t('humano.console.coupons.percent') },
    { value: 'FIXED', label: this.t('humano.console.coupons.fixed') },
  ]);
  protected readonly couponCycleOptions = computed(() => [
    { value: '', label: this.t('humano.console.coupons.anyCycle') },
    { value: 'MONTHLY', label: this.t('humano.console.coupons.monthlyOnly') },
    { value: 'YEARLY', label: this.t('humano.console.coupons.yearlyOnly') },
  ]);

  constructor() {
    this.console.ensureCurrency();
    effect(() => {
      const t = this.tab();
      if (t === 'invoices' && this.invoices() === null) this.loadInvoices();
      if (t === 'plans' && this.plans() === null) this.loadPlans();
      if (t === 'coupons' && this.coupons() === null) this.loadCoupons();
      // Coupon limits show plan names.
      if (t === 'coupons' && this.plans() === null) this.loadPlans();
      if (t === 'addons' && this.addons() === null) this.loadAddons();
    });
  }

  protected setTab(id: string | undefined): void {
    if (id) void this.router.navigate(['/platform/billing', id]);
  }

  // ---- Add-ons

  protected loadAddons(): void {
    this.addonsError.set(null);
    this.billing.addons().subscribe({
      next: list => this.addons.set(list),
      error: (err: unknown) => {
        this.addonsError.set(normalizeHttpError(err));
        this.addons.set([]);
      },
    });
  }

  // ---- Invoices

  protected loadInvoices(): void {
    this.invoicesError.set(null);
    this.billing.invoices().subscribe({
      next: page => this.invoices.set(page.content),
      error: (err: unknown) => {
        this.invoicesError.set(normalizeHttpError(err));
        this.invoices.set([]);
      },
    });
  }

  protected isOpen(i: Invoice): boolean {
    return i.status === InvoiceStatus.PENDING || i.status === InvoiceStatus.OVERDUE;
  }

  protected isLate(i: Invoice): boolean {
    return i.status === InvoiceStatus.OVERDUE || (i.status === InvoiceStatus.PENDING && !!i.dueDate && Date.parse(i.dueDate) < this.now);
  }

  protected applyInvoiceAction(): void {
    const a = this.invoiceAction();
    if (!a) return;
    const call: Observable<unknown> =
      a.action === 'pay'
        ? this.billing.markInvoicePaid(a.invoice.id)
        : a.action === 'overdue'
          ? this.billing.markInvoiceOverdue(a.invoice.id)
          : this.billing.voidInvoice(a.invoice.id);
    this.run(call, `humano.console.customer.done.invoice.${a.action}`, () => {
      this.invoiceAction.set(null);
      this.loadInvoices();
    });
  }

  // ---- Plans

  protected loadPlans(): void {
    this.plansApi.query({ page: 0, size: 200 }).subscribe({
      next: page => this.plans.set([...page.content].sort((a, b) => Number(a.basePrice ?? a.price) - Number(b.basePrice ?? b.price))),
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.plans.set([]);
      },
    });
    this.console.overview().subscribe({ next: o => this.planStats.set(new Map(o.plans.map(p => [p.planId, p]))) });
    this.billing
      .comparison()
      .subscribe({ next: c => this.comparison.set(c), error: () => this.comparison.set({ features: [], plans: [] }) });
  }

  /** A comparison cell as text: "150 employees", "Unlimited", a note, or null for a plain ✓ / —. */
  protected cellText(f: PlanFeatureDef, cell: PlanFeatureCell | undefined): string | null {
    if (!cell?.included) return null;
    if (f.limitType === 'QUANTITY') {
      return cell.limitValue === null ? this.t('humano.console.plans.unlimited') : this.quantity(cell.limitValue, f.unit);
    }
    return cell.note;
  }

  protected toggleExpanded(id: string): void {
    this.expanded.update(set => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
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

  protected newPlan(): void {
    this.planDraft.set({
      id: null,
      name: '',
      type: SubscriptionType.BASIC,
      price: '',
      description: '',
      featured: false,
      publicListed: true,
    });
    this.loadDraftItems(null);
  }

  protected editPlan(p: SubscriptionPlan): void {
    this.planDraft.set({
      id: p.id,
      name: p.displayName ?? '',
      type: p.subscriptionType,
      price: String(p.basePrice ?? p.price),
      description: p.description ?? '',
      featured: p.featured,
      publicListed: p.publicListed,
    });
    this.loadDraftItems(p.id);
  }

  protected patchItem(featureId: string, patch: Partial<{ included: boolean; limit: string; note: string }>): void {
    this.draftItems.update(d => ({ ...d, [featureId]: { ...d[featureId], ...patch } }));
  }

  protected patchPlanDraft(patch: Partial<PlanDraft>): void {
    this.planDraft.update(d => (d ? { ...d, ...patch } : d));
  }

  protected planDraftValid(): boolean {
    const d = this.planDraft();
    return !!d && d.name.trim().length > 0 && d.price !== '' && Number(d.price) >= 0;
  }

  protected savePlan(): void {
    const d = this.planDraft();
    if (!d || !this.planDraftValid()) return;
    const price = Number(d.price);
    const body = {
      subscriptionType: d.type as SubscriptionType,
      displayName: d.name.trim(),
      price,
      basePrice: price,
      description: d.description.trim(),
      featured: d.featured,
      publicListed: d.publicListed,
    };
    const cells = this.draftCells();
    const call: Observable<unknown> = (d.id ? this.billing.updatePlan(d.id, body) : this.billing.createPlan(body)).pipe(
      switchMap(plan => this.billing.setPlanFeatures(plan.id, cells)),
    );
    this.run(call, d.id ? 'humano.console.plans.saved' : 'humano.console.plans.created', () => {
      this.planDraft.set(null);
      this.loadPlans();
    });
  }

  protected togglePlan(p: SubscriptionPlan, active: boolean): void {
    this.run(this.billing.setPlanActive(p.id, active), active ? 'humano.console.plans.enabled' : 'humano.console.plans.retired', () =>
      this.loadPlans(),
    );
  }

  // ---- Feature catalog

  protected newFeature(): void {
    this.featureDraft.set({ id: null, name: '', category: this.categories()[0] ?? '', limitType: 'BOOLEAN', unit: '', description: '' });
  }

  protected editFeature(f: PlanFeatureDef): void {
    this.featureDraft.set({
      id: f.id,
      name: f.name,
      category: f.category ?? '',
      limitType: f.limitType === 'QUANTITY' ? 'QUANTITY' : 'BOOLEAN',
      unit: f.unit ?? '',
      description: f.description ?? '',
    });
  }

  protected patchFeatureDraft(
    patch: Partial<{ name: string; category: string; limitType: 'BOOLEAN' | 'QUANTITY'; unit: string; description: string }>,
  ): void {
    this.featureDraft.update(d => (d ? { ...d, ...patch } : d));
  }

  protected saveFeature(): void {
    const d = this.featureDraft();
    if (!d || d.name.trim().length < 2) return;
    const existing = d.id ? this.comparison()?.features.find(f => f.id === d.id) : undefined;
    const body = {
      name: d.name.trim(),
      category: d.category.trim() || undefined,
      limitType: d.limitType,
      unit: d.limitType === 'QUANTITY' ? d.unit.trim() || undefined : undefined,
      description: d.description.trim() || undefined,
      sortOrder: existing?.sortOrder ?? this.comparison()?.features.length ?? 0,
    };
    const call: Observable<unknown> = d.id ? this.billing.updateFeature(d.id, body) : this.billing.createFeature(body);
    this.run(call, d.id ? 'humano.console.features.saved' : 'humano.console.features.created', () => {
      this.featureDraft.set(null);
      this.loadPlans();
    });
  }

  protected deleteFeature(): void {
    const f = this.featureDelete();
    if (!f) return;
    this.run(this.billing.deleteFeature(f.id), 'humano.console.features.deleted', () => {
      this.featureDelete.set(null);
      this.featureDraft.set(null);
      this.loadPlans();
    });
  }

  // ---- Coupons

  protected loadCoupons(): void {
    this.billing.coupons().subscribe({
      next: page => this.coupons.set([...page.content].sort((a, b) => Number(b.active) - Number(a.active) || a.code.localeCompare(b.code))),
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.coupons.set([]);
      },
    });
  }

  protected newCoupon(): void {
    const expiry = new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 10);
    this.couponDraft.set({ code: '', type: 'PERCENT', value: '', expiry, max: '', cycle: '', planIds: [] });
    // The plans a coupon can be limited to.
    if (this.plans() === null) this.loadPlans();
  }

  protected patchCouponDraft(patch: Partial<CouponDraft>): void {
    this.couponDraft.update(d => (d ? { ...d, ...patch } : d));
  }

  protected toggleCouponPlan(planId: string, on: boolean): void {
    this.couponDraft.update(d =>
      d ? { ...d, planIds: on ? [...d.planIds.filter(id => id !== planId), planId] : d.planIds.filter(id => id !== planId) } : d,
    );
  }

  /** "Yearly · Growth, Enterprise", or "Any plan" for a coupon without limits. */
  protected couponScope(c: Coupon): string {
    const names = new Map((this.plans() ?? []).map(p => [p.id, p.displayName ?? p.subscriptionType]));
    const plans = c.planIds.map(id => names.get(id) ?? '?').join(', ');
    const parts = [
      c.billingCycle
        ? this.t(c.billingCycle === 'YEARLY' ? 'humano.console.coupons.yearlyOnly' : 'humano.console.coupons.monthlyOnly')
        : '',
      plans,
    ].filter(Boolean);
    return parts.length ? parts.join(' · ') : this.t('humano.console.coupons.anyPlan');
  }

  protected couponDraftValid(): boolean {
    const d = this.couponDraft();
    if (!d) return false;
    const v = Number(d.value);
    return /^[A-Z0-9_-]{3,50}$/.test(d.code) && d.value !== '' && v > 0 && (d.type === 'FIXED' || v <= 100) && !!d.expiry;
  }

  protected saveCoupon(): void {
    const d = this.couponDraft();
    if (!d || !this.couponDraftValid()) return;
    const value = Number(d.value);
    this.run(
      this.billing.createCoupon({
        code: d.code,
        type: d.type,
        discount: d.type === 'FIXED' ? value : 0,
        percentage: d.type === 'PERCENT' ? value : undefined,
        expiryDate: new Date(`${d.expiry}T23:59:59`).toISOString(),
        maxRedemptions: d.max ? Number(d.max) : undefined,
        billingCycle: d.cycle || undefined,
        planIds: d.planIds.length ? d.planIds : undefined,
      }),
      'humano.console.coupons.created',
      () => {
        this.couponDraft.set(null);
        this.loadCoupons();
      },
    );
  }

  protected deactivateCoupon(c: Coupon): void {
    this.run(this.billing.deactivateCoupon(c.id), 'humano.console.coupons.deactivated', () => this.loadCoupons());
  }

  protected deleteCoupon(): void {
    const c = this.couponDelete();
    if (!c) return;
    this.run(this.billing.deleteCoupon(c.id), 'humano.console.coupons.deleted', () => {
      this.couponDelete.set(null);
      this.loadCoupons();
    });
  }

  protected couponExpired(c: Coupon): boolean {
    return Date.parse(c.expiryDate) < this.now;
  }

  protected couponUsage(c: Coupon): number {
    return c.maxRedemptions ? Math.min(100, ((c.timesRedeemed ?? 0) / c.maxRedemptions) * 100) : 0;
  }

  private highlightLabel(f: PlanFeatureDef, cell: PlanFeatureCell): string {
    if (f.limitType === 'QUANTITY') {
      return cell.limitValue === null
        ? String(this.translate.instant('humano.console.plans.unlimitedOf', { name: this.lowerFirst(f.name) }))
        : this.quantity(cell.limitValue, f.unit ?? this.lowerFirst(f.name));
    }
    return cell.note ? `${f.name}: ${cell.note}` : f.name;
  }

  /** "1 admin", "25 employees", "1 country" — singular for one (English units). */
  private quantity(n: number, unit: string | null): string {
    if (!unit) return n.toLocaleString();
    const singular = n === 1 ? unit.replace(/ies$/, 'y').replace(/(?<!s)s$/, '') : unit;
    return `${n.toLocaleString()} ${singular}`;
  }

  /** "Document storage" → "document storage", but "HR admin seats" stays. */
  private lowerFirst(text: string): string {
    return /^[A-Z][a-z]/.test(text) ? text.charAt(0).toLowerCase() + text.slice(1) : text;
  }

  /** Higher = better: not included < limit (by size) < unlimited. */
  private rank(f: PlanFeatureDef, cell: PlanFeatureCell | undefined): number {
    if (!cell?.included) return -1;
    if (f.limitType !== 'QUANTITY') return 0;
    return cell.limitValue ?? Number.MAX_SAFE_INTEGER;
  }

  private loadDraftItems(planId: string | null): void {
    const cells = planId ? this.planColumns().find(c => c.plan.id === planId)?.cells : undefined;
    const draft: Record<string, { included: boolean; limit: string; note: string }> = {};
    for (const f of this.comparison()?.features ?? []) {
      const c = cells?.get(f.id);
      draft[f.id] = { included: !!c?.included, limit: c?.limitValue != null ? String(c.limitValue) : '', note: c?.note ?? '' };
    }
    this.draftItems.set(draft);
  }

  private draftCells(): PlanFeatureCell[] {
    return Object.entries(this.draftItems()).map(([featureId, v]) => ({
      featureId,
      included: v.included,
      limitValue: v.included && v.limit !== '' ? Math.max(0, Math.round(Number(v.limit))) : null,
      note: v.note.trim() || null,
    }));
  }

  private run(call: Observable<unknown>, doneKey: string, after: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(this.t(doneKey));
        after();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
