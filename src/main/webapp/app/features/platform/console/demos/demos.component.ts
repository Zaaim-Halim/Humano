import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { BadgeTone } from 'app/config/status-map';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  SwitchComponent,
  TabItem,
  TabsComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';
import { countryName, countryOptions } from 'app/shared/util/countries';

import { BillingCycle } from '../../models/enums/billing-cycle.enum';
import { DemoCredentials, DemoRequest, DemoRequestStatus, DemoWorkspace } from '../../models/demo.model';
import { SubscriptionPlan } from '../../models/subscription-plan.model';
import { PlatformDemoService } from '../../services/platform-demo.service';
import { SubscriptionPlanService } from '../../services/subscription-plan.service';

type Tab = 'requests' | 'workspaces';
type RequestFilter = 'open' | 'all';

interface CreateDraft {
  companyName: string;
  subdomain: string;
  subdomainTouched: boolean;
  country: string;
  timezone: string;
  contactName: string;
  contactEmail: string;
  durationDays: number;
  planId: string;
  sampleData: boolean;
  demoRequestId?: string;
}

interface ConvertDraft {
  demo: DemoWorkspace;
  planId: string;
  billingCycle: BillingCycle;
  trialDays: number;
  couponCode: string;
}

const DAY = 24 * 60 * 60 * 1000;

/** `Acme Corp.` → `acme-corp` (3–63 chars, as the server requires). */
export function toSubdomain(name: string): string {
  return name
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
    .replace(/-+$/g, '');
}

/**
 * Owner console — demos (`/platform/demos/:tab`). **Requests** is the inbox of the public site's
 * "Book a demo" form: mark one contacted or declined, keep notes, or set up a demo for it.
 * **Workspaces** are the demo tenants set up for prospects, filled with a sample company: they
 * set up in the background (the list polls until they are ready), run until they expire, and can
 * be extended, expired, converted to a paying customer or deleted. Sign-in details are shown once,
 * when a demo is created or its password reset.
 */
@Component({
  selector: 'hum-console-demos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
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
    TextareaComponent,
  ],
  templateUrl: './demos.component.html',
})
export default class ConsoleDemosComponent {
  private readonly api = inject(PlatformDemoService);
  private readonly plansApi = inject(SubscriptionPlanService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly account = inject(AccountService).trackCurrentAccount();

  readonly tabParam = input<string | undefined>(undefined, { alias: 'tab' });
  protected readonly tab = computed<Tab>(() => (this.tabParam() === 'workspaces' ? 'workspaces' : 'requests'));

  protected readonly requests = signal<DemoRequest[] | null>(null);
  protected readonly demos = signal<DemoWorkspace[] | null>(null);
  protected readonly plans = signal<SubscriptionPlan[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly requestFilter = signal<RequestFilter>('open');
  protected readonly now = signal(Date.now());

  protected readonly can = computed(() => {
    const p = this.account()?.permissions ?? [];
    return {
      provision: p.includes(Permission.PROVISION_TENANT),
      suspend: p.includes(Permission.SUSPEND_TENANT),
      billing: p.includes(Permission.MANAGE_PLATFORM_BILLING),
      deprovision: p.includes(Permission.DEPROVISION_TENANT),
    };
  });

  // ---- Dialogs
  protected readonly viewing = signal<DemoRequest | null>(null);
  protected readonly notesDraft = signal('');
  protected readonly create = signal<CreateDraft | null>(null);
  protected readonly credentials = signal<DemoCredentials | null>(null);
  protected readonly extending = signal<{ demo: DemoWorkspace; days: number } | null>(null);
  protected readonly converting = signal<ConvertDraft | null>(null);
  protected readonly confirming = signal<{ demo: DemoWorkspace; action: 'expire' | 'delete' | 'reset' } | null>(null);

  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'requests', label: this.t('humano.console.demos.tabs.requests'), icon: 'inbox', count: this.counts().newRequests || undefined },
    {
      id: 'workspaces',
      label: this.t('humano.console.demos.tabs.workspaces'),
      icon: 'presentation',
      count: this.counts().live || undefined,
    },
  ]);

  protected readonly counts = computed(() => {
    const reqs = this.requests() ?? [];
    const demos = this.demos() ?? [];
    const now = this.now();
    const live = demos.filter(d => d.status === 'ACTIVE' || d.setup === 'PROVISIONING' || d.setup === 'SEEDING');
    return {
      newRequests: reqs.filter(r => r.status === 'NEW').length,
      openRequests: reqs.filter(r => r.status === 'NEW' || r.status === 'CONTACTED').length,
      live: live.length,
      expiringSoon: live.filter(d => d.expiresAt && Date.parse(d.expiresAt) - now < 3 * DAY && Date.parse(d.expiresAt) > now).length,
      expired: demos.filter(d => d.status === 'SUSPENDED').length,
    };
  });

  protected readonly visibleRequests = computed(() => {
    const list = this.requests() ?? [];
    return this.requestFilter() === 'open' ? list.filter(r => r.status === 'NEW' || r.status === 'CONTACTED') : list;
  });

  protected readonly planOptions = computed<SelectOption[]>(() =>
    this.plans().map(p => ({ value: p.id, label: p.displayName ?? p.subscriptionType })),
  );
  protected readonly countryOptions = computed<SelectOption[]>(() => countryOptions(this.translate.currentLang || 'en'));
  protected readonly durationOptions = computed<SelectOption[]>(() =>
    [7, 14, 30, 60, 90].map(d => ({ value: String(d), label: this.t('humano.console.demos.days', { n: d }) })),
  );
  protected readonly cycleOptions = computed<SelectOption[]>(() =>
    [BillingCycle.MONTHLY, BillingCycle.YEARLY].map(c => ({ value: c, label: this.t(`humano.console.cycle.${c}`) })),
  );
  /** Polls while any demo is still being set up. */
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.load();
    this.plansApi.listActive().subscribe({ next: list => this.plans.set(list), error: () => undefined });
    effect(() => {
      const settingUp = (this.demos() ?? []).some(d => d.setup === 'PROVISIONING' || d.setup === 'SEEDING');
      if (settingUp && !this.pollHandle) {
        this.pollHandle = setInterval(() => this.loadDemos(), 5000);
      } else if (!settingUp && this.pollHandle) {
        clearInterval(this.pollHandle);
        this.pollHandle = null;
      }
    });
    inject(DestroyRef).onDestroy(() => {
      if (this.pollHandle) clearInterval(this.pollHandle);
    });
  }

  protected load(): void {
    this.error.set(null);
    this.api.requests().subscribe({
      next: page => this.requests.set(page.content),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
    this.loadDemos();
  }

  protected setTab(id: string | undefined): void {
    if (id) void this.router.navigate(['/platform/demos', id]);
  }

  // ---- Requests

  protected requestTone(status: DemoRequestStatus): BadgeTone {
    return ({ NEW: 'brand', CONTACTED: 'info', DEMO_CREATED: 'success', DECLINED: 'neutral' } as const)[status];
  }

  protected openRequest(r: DemoRequest): void {
    this.viewing.set(r);
    this.notesDraft.set(r.notes ?? '');
  }

  protected setRequestStatus(r: DemoRequest, status: DemoRequestStatus): void {
    this.run(this.api.updateRequest(r.id, { status }), `humano.console.demos.request.marked.${status}`, () => this.viewing.set(null));
  }

  protected saveNotes(): void {
    const r = this.viewing();
    if (!r) return;
    this.run(this.api.updateRequest(r.id, { notes: this.notesDraft() }), 'humano.console.demos.request.notesSaved', () =>
      this.viewing.set(null),
    );
  }

  protected deleteRequest(r: DemoRequest): void {
    this.run(this.api.deleteRequest(r.id), 'humano.console.demos.request.deleted', () => this.viewing.set(null));
  }

  // ---- Create

  protected openCreate(from?: DemoRequest): void {
    this.viewing.set(null);
    const featured = this.plans().find(p => p.active && p.featured);
    this.create.set({
      companyName: from?.companyName ?? '',
      subdomain: from ? toSubdomain(from.companyName) : '',
      subdomainTouched: false,
      country: from?.country ?? 'US',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      contactName: from?.fullName ?? '',
      contactEmail: from?.workEmail ?? '',
      durationDays: 14,
      planId: featured?.id ?? this.plans()[0]?.id ?? '',
      sampleData: true,
      demoRequestId: from?.id,
    });
  }

  protected patchCreate(patch: Partial<CreateDraft>): void {
    this.create.update(d => {
      if (!d) return d;
      const next = { ...d, ...patch };
      if (patch.companyName !== undefined && !d.subdomainTouched) next.subdomain = toSubdomain(patch.companyName);
      return next;
    });
  }

  protected createValid(): boolean {
    const d = this.create();
    return (
      !!d &&
      d.companyName.trim().length >= 2 &&
      /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/.test(d.subdomain) &&
      d.subdomain.length >= 3 &&
      !!d.country &&
      (!d.contactEmail.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(d.contactEmail.trim()))
    );
  }

  protected submitCreate(): void {
    const d = this.create();
    if (!d || !this.createValid()) return;
    this.busy.set(true);
    this.api
      .create({
        companyName: d.companyName.trim(),
        subdomain: d.subdomain,
        country: d.country,
        timezone: d.timezone,
        contactName: d.contactName.trim() || undefined,
        contactEmail: d.contactEmail.trim() || undefined,
        durationDays: d.durationDays,
        planId: d.planId || undefined,
        sampleData: d.sampleData,
        demoRequestId: d.demoRequestId,
      })
      .subscribe({
        next: created => {
          this.busy.set(false);
          this.create.set(null);
          this.credentials.set(created.credentials);
          this.toast.success(this.t('humano.console.demos.created'));
          this.setTab('workspaces');
          this.load();
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  // ---- Workspaces

  protected setupLabel(d: DemoWorkspace): { tone: BadgeTone; key: string } {
    if (d.setup === 'FAILED') return { tone: 'danger', key: 'failed' };
    if (d.setup === 'PROVISIONING' || d.setup === 'SEEDING') return { tone: 'info', key: d.setup.toLowerCase() };
    if (d.status === 'SUSPENDED') return { tone: 'neutral', key: 'expired' };
    if (this.expiresSoon(d)) return { tone: 'warning', key: 'expiring' };
    return { tone: 'success', key: 'live' };
  }

  protected expiresSoon(d: DemoWorkspace): boolean {
    return !!d.expiresAt && Date.parse(d.expiresAt) - this.now() < 3 * DAY;
  }

  protected daysLeft(d: DemoWorkspace): number {
    return d.expiresAt ? Math.max(0, Math.ceil((Date.parse(d.expiresAt) - this.now()) / DAY)) : 0;
  }

  protected retry(d: DemoWorkspace): void {
    this.busy.set(true);
    this.api.retry(d.id).subscribe({
      next: created => {
        this.busy.set(false);
        this.credentials.set(created.credentials);
        this.loadDemos();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected submitExtend(): void {
    const e = this.extending();
    if (!e) return;
    this.run(this.api.extend(e.demo.id, e.days), 'humano.console.demos.extended', () => this.extending.set(null));
  }

  protected confirm(): void {
    const c = this.confirming();
    if (!c) return;
    if (c.action === 'reset') {
      this.busy.set(true);
      this.api.resetPassword(c.demo.id).subscribe({
        next: creds => {
          this.busy.set(false);
          this.confirming.set(null);
          this.credentials.set(creds);
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
      return;
    }
    const call = c.action === 'expire' ? this.api.expire(c.demo.id) : this.api.remove(c.demo.id);
    this.run(call, `humano.console.demos.${c.action}d`, () => this.confirming.set(null));
  }

  protected openConvert(d: DemoWorkspace): void {
    this.converting.set({ demo: d, planId: d.planId ?? '', billingCycle: BillingCycle.MONTHLY, trialDays: 0, couponCode: '' });
  }

  protected patchConvert(patch: Partial<ConvertDraft>): void {
    this.converting.update(c => (c ? { ...c, ...patch } : c));
  }

  protected submitConvert(): void {
    const c = this.converting();
    if (!c?.planId) return;
    this.run(
      this.api.convert(c.demo.id, {
        planId: c.planId,
        billingCycle: c.billingCycle,
        trialDays: c.trialDays > 0 ? c.trialDays : undefined,
        couponCode: c.couponCode.trim() || undefined,
      }),
      'humano.console.demos.converted',
      () => {
        this.converting.set(null);
        void this.router.navigate(['/platform/customers', c.demo.id]);
      },
    );
  }

  protected copy(text: string): void {
    void navigator.clipboard?.writeText(text).then(() => this.toast.success(this.t('humano.console.demos.copied')));
  }

  protected credentialsText(c: DemoCredentials): string {
    return [
      `${this.t('humano.console.demos.creds.workspace')}: ${c.workspace}`,
      `${this.t('humano.console.demos.creds.admin')}: ${c.adminLogin}`,
      `${this.t('humano.console.demos.creds.others')}: ${c.personaLogins.join(', ')}`,
      `${this.t('humano.console.demos.creds.password')}: ${c.password}`,
    ].join('\n');
  }

  protected countryName(code: string | null): string {
    return countryName(code, this.translate.currentLang || 'en');
  }

  private loadDemos(): void {
    this.api.demos().subscribe({
      next: list => {
        this.now.set(Date.now());
        this.demos.set(list);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  private run(call: Observable<unknown>, doneKey: string, after?: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.toast.success(this.t(doneKey));
        this.load();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
        this.load();
      },
    });
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
