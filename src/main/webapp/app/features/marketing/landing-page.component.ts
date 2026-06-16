import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AccountService } from 'app/core/auth/account.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { AvatarComponent, BadgeComponent, ButtonComponent } from 'app/shared/ui';

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
  imports: [RouterLink, TranslatePipe, LucideAngularModule, ButtonComponent, BadgeComponent, AvatarComponent],
  templateUrl: './landing-page.component.html',
})
export default class LandingPageComponent {
  protected readonly theme = inject(ThemeService);
  private readonly accountService = inject(AccountService);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly authenticated = computed(() => !!this.account());

  /** Open FAQ item index (single-open accordion); first item open by default. */
  protected readonly openFaq = signal(0);

  protected readonly navLinks = ['Product', 'Solutions', 'Pricing', 'FAQ', 'Docs'];

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

  protected readonly tiers: Tier[] = [
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
      q: 'How much does it cost per employee?',
      a: 'Plans start at $6 per employee per month on Starter and $12 on Growth, billed annually. Enterprise is volume-priced — talk to us.',
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

  constructor() {
    // Public route runs no guard, so resolve identity here to drive the auth-aware nav.
    // identity() caches, so this is a no-op when already loaded.
    this.accountService.identity().pipe(takeUntilDestroyed()).subscribe();
  }

  protected toggleFaq(index: number): void {
    this.openFaq.update(open => (open === index ? -1 : index));
  }
}
