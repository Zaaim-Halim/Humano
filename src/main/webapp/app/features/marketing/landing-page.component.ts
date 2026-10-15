import { ChangeDetectionStrategy, Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AccountService } from 'app/core/auth/account.service';
import { ThemeService } from 'app/core/theme/theme.service';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  TextareaComponent,
} from 'app/shared/ui';
import { countryOptions } from 'app/shared/util/countries';

import { DemoRequestForm, MarketingService, PublicPlan, PublicPricing } from './marketing.service';

interface Persona {
  icon: string;
  role: string;
  title: string;
  points: string[];
}
interface Feature {
  icon: string;
  title: string;
  desc: string;
}
interface Tier {
  name: string;
  desc: string;
  price: string;
  unit: string;
  note: string;
  features: string[];
  cta: string;
  featured: boolean;
}

/** Yearly billing discount, as the console and the server bill it (12 × 85%). */
const YEARLY_FACTOR = 0.85;
const COMPANY_SIZES = ['1-10', '11-50', '51-200', '201-500', '501-1000', '1000+'];
interface Faq {
  q: string;
  a: string;
}

/**
 * Marketing landing page — the public acquisition surface at `/`. Reuses the
 * Humano design system (tokens + `.hum-*` primitives); landing-specific layout
 * lives under the `.lp-*` block in humano.scss. Auth-aware: signed-in visitors
 * get an "Open app" link instead of the sign-in / trial CTAs.
 */
@Component({
  selector: 'hum-landing-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    BadgeComponent,
    AvatarComponent,
    DialogComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    TextareaComponent,
  ],
  templateUrl: './landing-page.component.html',
})
export default class LandingPageComponent {
  protected readonly theme = inject(ThemeService);
  private readonly accountService = inject(AccountService);
  private readonly marketing = inject(MarketingService);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly authenticated = computed(() => !!this.account());

  /** Open FAQ item index (single-open accordion); first item open by default. */
  protected readonly openFaq = signal(0);

  protected readonly navLinks = [
    { label: 'Product', href: '#product' },
    { label: 'Pricing', href: '#pricing' },
    { label: 'FAQ', href: '#faq' },
  ];

  protected readonly stats = [
    { figure: '2.4M+', label: 'Payslips processed / month' },
    { figure: '150+', label: 'Countries supported' },
    { figure: '99.99%', label: 'Platform uptime' },
    { figure: '4.9/5', label: 'Average admin rating' },
  ];

  protected readonly personas: Persona[] = [
    {
      icon: 'users',
      role: 'HR & Admin',
      title: 'Run the org with confidence',
      points: ['One source of truth for people & roles', 'Automated, compliant payroll runs', 'Audit-ready records by default'],
    },
    {
      icon: 'check-check',
      role: 'Managers',
      title: 'Approvals without the chase',
      points: ['Time off and expenses in one queue', 'Clear team capacity at a glance', 'Decisions logged and traceable'],
    },
    {
      icon: 'user',
      role: 'Employees',
      title: 'Everything in one place',
      points: ['Payslips and tax docs on demand', 'Request leave in seconds', 'Always-current personal details'],
    },
  ];

  protected readonly features: Feature[] = [
    { icon: 'users', title: 'People & directory', desc: 'A living org chart and profiles every team can trust as the source of truth.' },
    { icon: 'wallet', title: 'Payroll runs', desc: 'Multi-country runs with automated taxes, deductions and payslip generation.' },
    { icon: 'palmtree', title: 'Time & leave', desc: 'Balances, policies and approvals that stay in sync with payroll automatically.' },
    { icon: 'trending-up', title: 'Performance', desc: 'Lightweight reviews and goals that fit how your teams actually work.' },
    { icon: 'receipt', title: 'Billing & invoices', desc: 'Transparent usage, invoices and receipts — no surprises at month-end.' },
    { icon: 'network', title: 'Multi-tenant platform', desc: 'Isolated workspaces with platform-grade controls for groups and resellers.' },
  ];

  /** Shown until the live plans load, and if they can't. */
  private readonly fallbackTiers: Tier[] = [
    {
      name: 'Starter',
      desc: 'For small teams getting their people ops in order.',
      price: '$6',
      unit: '/ employee / mo',
      note: 'Billed annually. Minimum 5 seats.',
      features: ['People directory', 'Time & leave', 'Single-country payroll', 'Email support'],
      cta: 'Start free trial',
      featured: false,
    },
    {
      name: 'Growth',
      desc: 'For scaling companies that need payroll everywhere.',
      price: '$12',
      unit: '/ employee / mo',
      note: 'Billed annually. Minimum 10 seats.',
      features: ['Everything in Starter', 'Multi-country payroll', 'Approvals & performance', 'Priority support'],
      cta: 'Start free trial',
      featured: true,
    },
    {
      name: 'Enterprise',
      desc: 'For groups, resellers and complex org structures.',
      price: 'Custom',
      unit: '',
      note: 'Volume pricing & platform controls.',
      features: ['Everything in Growth', 'Multi-tenant platform', 'SSO & advanced security', 'Dedicated success manager'],
      cta: 'Book a demo',
      featured: false,
    },
  ];

  protected readonly faqs: Faq[] = [
    {
      q: 'How long does setup take?',
      a: 'Most teams are live within a day. Import your people via CSV or our API, pick your payroll regions, and invite your team — no implementation project required.',
    },
    {
      q: 'Do you support multi-country payroll?',
      a: 'Yes. Humano runs payroll in 150+ regions with localized taxes, deductions and payslips, all from a single workspace.',
    },
    {
      q: 'How is my data secured?',
      a: 'Data is encrypted in transit and at rest, isolated per tenant, and backed by SOC 2 Type II controls with audit logging throughout.',
    },
    {
      q: 'How much does it cost?',
      a: 'Each plan has a flat monthly price per workspace, listed above; paying yearly saves 15%. Not sure which fits? Book a demo and we will set up a workspace for you to try.',
    },
    {
      q: 'What happens after the trial?',
      a: 'Your 14-day trial needs no credit card. When it ends you can pick a plan to keep going, or your workspace simply pauses — nothing is deleted.',
    },
  ];

  protected readonly footerColumns = [
    { heading: 'Product', links: ['Features', 'Pricing', 'Security', 'Integrations'] },
    { heading: 'Company', links: ['About', 'Careers', 'Blog', 'Contact'] },
    { heading: 'Resources', links: ['Docs', 'API', 'Status', 'Changelog'] },
    { heading: 'Legal', links: ['Privacy', 'Terms', 'DPA', 'Cookies'] },
  ];

  protected readonly year = new Date().getFullYear();

  /** The live plans (`/api/public/pricing`); null until loaded or if they can't be. */
  private readonly pricing = signal<PublicPricing | null>(null);

  protected readonly tiers = computed<Tier[]>(() => {
    const p = this.pricing();
    if (!p?.plans.length) return this.fallbackTiers;
    const featureById = new Map(p.features.map(f => [f.id, f]));
    const money = new Intl.NumberFormat('en', { style: 'currency', currency: p.currency, maximumFractionDigits: 0 });
    const label = (it: PublicPlan['items'][number]): string => {
      const f = featureById.get(it.featureId)!;
      const unit = f.unit ?? f.name.toLowerCase();
      if (f.limitType === 'QUANTITY') {
        return it.limitValue === null ? `Unlimited ${unit}` : `Up to ${it.limitValue} ${unit}`;
      }
      return it.note ? `${f.name} — ${it.note}` : f.name;
    };
    return p.plans.map((plan, i) => {
      const monthly = plan.monthlyPrice;
      const previous = i > 0 ? p.plans[i - 1] : null;
      // After the first plan, list only what this one adds or raises over the previous one.
      const before = new Map((previous?.items ?? []).filter(it => it.included).map(it => [it.featureId, label(it)]));
      const lines = plan.items
        .filter(it => it.included && featureById.has(it.featureId))
        .filter(it => before.get(it.featureId) !== label(it))
        .map(label);
      return {
        name: plan.name,
        desc: plan.description ?? '',
        price: monthly > 0 ? money.format(monthly) : 'Free',
        unit: monthly > 0 ? '/ month' : '',
        note: monthly > 0 ? `${money.format(monthly * YEARLY_FACTOR)} / month billed yearly` : 'No credit card needed',
        features: [...(previous ? [`Everything in ${previous.name}, plus`] : []), ...lines.slice(0, previous ? 5 : 6)],
        cta: 'Book a demo',
        featured: plan.featured,
      };
    });
  });

  // ---- Book a demo
  protected readonly demoOpen = signal(false);
  protected readonly demoSent = signal(false);
  protected readonly demoSending = signal(false);
  protected readonly demoError = signal<string | null>(null);
  protected readonly demoForm = signal<Required<DemoRequestForm>>(this.emptyDemoForm());
  protected readonly sizeOptions = [{ value: '', label: 'Select…' }, ...COMPANY_SIZES.map(s => ({ value: s, label: `${s} employees` }))];
  protected readonly countryOptions = [{ value: '', label: 'Select…' }, ...countryOptions('en')];

  constructor() {
    // Public route runs no guard, so resolve identity here to drive the auth-aware nav.
    // identity() caches, so this is a no-op when already loaded.
    this.accountService.identity().pipe(takeUntilDestroyed()).subscribe();
    this.marketing
      .pricing()
      .pipe(takeUntilDestroyed())
      .subscribe({ next: p => this.pricing.set(p), error: () => this.pricing.set(null) });
    // Opened on a section link (e.g. `/#pricing`): the page renders after the browser's own jump.
    afterNextRender(() => {
      const id = location.hash.slice(1);
      if (id) document.getElementById(id)?.scrollIntoView();
    });
  }

  protected openDemo(plan?: string): void {
    this.demoSent.set(false);
    this.demoError.set(null);
    if (plan && !this.demoForm().message) this.patchDemo({ message: `Interested in the ${plan} plan.` });
    this.demoOpen.set(true);
  }

  protected patchDemo(patch: Partial<DemoRequestForm>): void {
    this.demoForm.update(f => ({ ...f, ...patch }));
  }

  protected demoValid(): boolean {
    const f = this.demoForm();
    return f.fullName.trim().length > 1 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.workEmail.trim()) && f.companyName.trim().length > 1;
  }

  protected sendDemo(): void {
    if (!this.demoValid() || this.demoSending()) return;
    const f = this.demoForm();
    this.demoSending.set(true);
    this.demoError.set(null);
    this.marketing
      .requestDemo({
        fullName: f.fullName.trim(),
        workEmail: f.workEmail.trim(),
        companyName: f.companyName.trim(),
        companySize: f.companySize || undefined,
        country: f.country || undefined,
        phone: f.phone.trim() || undefined,
        message: f.message.trim() || undefined,
        website: f.website || undefined,
      })
      .subscribe({
        next: () => {
          this.demoSending.set(false);
          this.demoSent.set(true);
          this.demoForm.set(this.emptyDemoForm());
        },
        error: () => {
          this.demoSending.set(false);
          this.demoError.set('We could not send your request. Please try again, or email sales@humano.app.');
        },
      });
  }

  private emptyDemoForm(): Required<DemoRequestForm> {
    return { fullName: '', workEmail: '', companyName: '', companySize: '', country: '', phone: '', message: '', website: '' };
  }

  protected toggleFaq(index: number): void {
    this.openFaq.update(open => (open === index ? -1 : index));
  }
}
