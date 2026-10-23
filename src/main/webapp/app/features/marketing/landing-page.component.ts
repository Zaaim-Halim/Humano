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

import { LandingShowcaseComponent } from './landing-showcase.component';
import { DemoRequestForm, MarketingService, PublicPlan, PublicPricing } from './marketing.service';
import { formatPlanAmount } from 'app/shared/util/plan-units';

interface Persona {
  icon: string;
  role: string;
  title: string;
  points: string[];
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
  /** Starts a trial of this plan; null: the tier is sold through a demo. */
  signup: { plan?: string } | null;
}

/** Yearly billing discount, as the console and the server bill it (12 × 85%). */
const YEARLY_FACTOR = 0.85;
const COMPANY_SIZES = ['1-10', '11-50', '51-200', '201-500', '501-1000', '1000+'];
/** One area of the product and what it does — only what the app actually ships. */
interface Capability {
  /** Which mini illustration heads the card. */
  visual: 'payroll' | 'people' | 'org' | 'time' | 'approvals' | 'self' | 'admin';
  /** Spans two columns of the grid. */
  wide: boolean;
  icon: string;
  area: string;
  summary: string;
  items: string[];
}
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
    LandingShowcaseComponent,
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
    { label: 'Features', href: '#features' },
    { label: 'Tour', href: '#tour' },
    { label: 'Pricing', href: '#pricing' },
    { label: 'FAQ', href: '#faq' },
  ];

  /** Facts about the product itself (not usage numbers). */
  protected readonly stats = [
    { figure: '14 days', label: 'Free trial, no credit card' },
    { figure: '7', label: 'Kinds of request in one approvals queue' },
    { figure: '11', label: 'Kinds of employee record, with expiry dates' },
    { figure: '1', label: 'Separate database for each company' },
  ];

  protected readonly personas: Persona[] = [
    {
      icon: 'users',
      role: 'HR & Admin',
      title: 'Run the org with confidence',
      points: [
        'One source of truth for people & roles',
        'Payroll runs you review before posting',
        'Permits and licences flagged before they expire',
      ],
    },
    {
      icon: 'check-check',
      role: 'Managers',
      title: 'Approvals without the chase',
      points: [
        'Time off, expenses and overtime in one queue',
        "Today's attendance for your team at a glance",
        'Every decision kept, with comments',
      ],
    },
    {
      icon: 'user',
      role: 'Employees',
      title: 'Everything in one place',
      points: ['Payslips whenever they need them', 'Request time off in seconds', 'Clock in and out from one place'],
    },
  ];

  protected readonly capabilities: Capability[] = [
    {
      visual: 'payroll',
      wide: true,
      icon: 'wallet',
      area: 'Payroll',
      summary: 'Runs you review before anyone is paid.',
      items: [
        'Runs move from draft to calculated, approved and posted',
        'Gross, deductions, net and employer cost, with the change from last period',
        'Pay rules written as formulas, with priorities and effective dates',
        'PDF payslips, and pay in other currencies with exchange rates',
        'Statutory tax and contributions for the Netherlands, Germany, the UK, the US, Bulgaria, Albania, Croatia and Romania — you or your accountant still file the payroll tax return',
      ],
    },
    {
      visual: 'people',
      wide: false,
      icon: 'users',
      area: 'People & records',
      summary: 'Every employee on one profile, with the paperwork that comes with them.',
      items: [
        '360° profile: pay, time off, documents, performance',
        'Contracts, permits, certifications and licences with expiry dates',
        'Bank accounts, emergency contacts, education and equipment',
        'A document library for each employee',
      ],
    },
    {
      visual: 'org',
      wide: false,
      icon: 'network',
      area: 'Organization',
      summary: 'Model the company as it is, and see who reports to whom.',
      items: ['Departments, positions and org units', 'Organization tree and people tree', 'Custom lists for your forms'],
    },
    {
      visual: 'time',
      wide: true,
      icon: 'timer',
      area: 'Time & attendance',
      summary: 'Know who is working today, and pay overtime right.',
      items: [
        'Clock in and out, with breaks',
        'Live team attendance, including missing clock-outs',
        'Timesheets, time off requests, balances and a team calendar',
        'Working hours and overtime rules set once for the company',
      ],
    },
    {
      visual: 'approvals',
      wide: false,
      icon: 'check-check',
      area: 'Approvals',
      summary: 'Every request that needs a yes lands in one queue.',
      items: ['Time off, expenses and overtime', 'Training, transfers and salary changes', 'Timesheets'],
    },
    {
      visual: 'self',
      wide: false,
      icon: 'user',
      area: 'Employee self-service',
      summary: 'A space where each person handles their own HR.',
      items: ['Payslips and net pay for the year', 'Time off requests and their status', 'Personal documents and profile'],
    },
    {
      visual: 'admin',
      wide: false,
      icon: 'shield-check',
      area: 'Administration & security',
      summary: 'Control who sees what, and keep each company apart.',
      items: ['Roles and permissions for every user', 'A separate database for each company', 'Search everything with ⌘K, light or dark'],
    },
  ];

  /** Shown until the live plans load, and if they can't. */
  private readonly fallbackTiers: Tier[] = [
    {
      name: 'Starter',
      desc: 'For small teams getting their people ops in order.',
      price: '$6',
      unit: '/ employee / mo',
      note: 'Billed annually. Minimum 5 seats.',
      features: ['Employee directory and records', 'Time off and attendance', 'Payroll runs and payslips', 'Employee self-service'],
      cta: 'Start free trial',
      featured: false,
      signup: {},
    },
    {
      name: 'Growth',
      desc: 'For scaling companies that need payroll everywhere.',
      price: '$12',
      unit: '/ employee / mo',
      note: 'Billed annually. Minimum 10 seats.',
      features: ['Everything in Starter', 'Approvals queue', 'Performance reviews', 'Pay in other currencies'],
      cta: 'Start free trial',
      featured: true,
      signup: {},
    },
    {
      name: 'Enterprise',
      desc: 'For groups, resellers and complex org structures.',
      price: 'Custom',
      unit: '',
      note: 'Volume pricing & platform controls.',
      features: ['Everything in Growth', 'Higher limits', 'Set up with our team', 'Custom terms'],
      cta: 'Book a demo',
      featured: false,
      signup: null,
    },
  ];

  protected readonly faqs: Faq[] = [
    {
      q: 'How long does setup take?',
      a: 'Start a trial and your workspace is ready in about a minute. Add your company details, create your employees and set up your pay rules; there is no implementation project.',
    },
    {
      q: 'Can we pay people in different currencies?',
      a: 'Yes. Set a default currency for the company and pay in other currencies using exchange rates. Your pay rules decide how each component is calculated.',
    },
    {
      q: 'Does Humano file payroll taxes for us?',
      a: 'No. Humano calculates pay, income tax and social contributions — for eight countries from statutory templates kept up to date for you — and produces payslips. It does not submit payroll tax returns or payments to tax authorities, such as the loonaangifte in the Netherlands, RTI in the UK or Form 941 in the US. Your accountant or a filing service files them, using the figures from your posted payroll runs.',
    },
    {
      q: 'How is my data secured?',
      a: 'Each company gets its own database, so your data is never mixed with another customer’s. Access is controlled by roles and permissions, and every approval keeps its history.',
    },
    {
      q: 'How much does it cost?',
      a: 'Each plan has a flat monthly price per workspace, listed above; paying yearly saves 15%. Try any plan free for 14 days, or book a demo and we will walk you through it.',
    },
    {
      q: 'What happens after the trial?',
      a: 'Your 14-day trial needs no credit card. When it ends, pick a plan to keep going. If you do not, your workspace turns read-only for 14 days and then pauses — nothing is deleted.',
    },
  ];

  /** Only places that exist: sections of this page and the account screens. */
  protected readonly footerColumns = [
    {
      heading: 'Product',
      links: [
        { label: 'Features', href: '#features' },
        { label: 'Tour', href: '#tour' },
        { label: 'Pricing', href: '#pricing' },
        { label: 'FAQ', href: '#faq' },
      ],
    },
    {
      heading: 'Get started',
      links: [
        { label: 'Start free trial', href: '/signup' },
        { label: 'Sign in', href: '/login' },
      ],
    },
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
        if (it.limitValue === null) return `Unlimited ${unit}`;
        const amount = formatPlanAmount(it.limitValue, f.unit);
        return `Up to ${amount.value} ${f.unit ? amount.unit : unit}`;
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
        cta: 'Start free trial',
        featured: plan.featured,
        signup: { plan: plan.id },
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
