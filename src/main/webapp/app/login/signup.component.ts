import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { LEGAL_VERSION } from 'app/features/legal/legal-info';
import { MarketingService, PublicPayrollTemplate, PublicPricing, SubdomainCheck } from 'app/features/marketing/marketing.service';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  FormFieldComponent,
  InputComponent,
  RadioComponent,
  SelectComponent,
} from 'app/shared/ui';
import { countryOptions } from 'app/shared/util/countries';

const SUBDOMAIN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;

/** A workspace address from a company name: lowercase, hyphenated, at most 25 characters. */
function slugify(name: string): string {
  return name
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 25)
    .replace(/-+$/g, '');
}

/** The "no template" choice in the payroll template radios. */
const NO_TEMPLATE = '';

/**
 * Start a free trial — `POST /api/public/signup`. The visitor picks a plan (preselected from the
 * pricing page's `?plan=`), names the company and its workspace address (checked as they type), and
 * gets a confirmation link by email; the workspace is only created once they open it.
 * <p>
 * Once a country is chosen, and if the plan includes payroll, the statutory payroll templates for
 * that country are offered (`GET /api/public/payroll-templates`): the first one is suggested, and
 * "no template" is always a choice. The chosen template is added to the workspace switched off; an
 * administrator fills in its settings before the first payroll run.
 */
@Component({
  selector: 'hum-signup',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen hum-auth-screen--wide' },
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    CheckboxComponent,
    FormFieldComponent,
    InputComponent,
    RadioComponent,
    SelectComponent,
  ],
  templateUrl: './signup.component.html',
})
export default class SignupComponent {
  private readonly fb = inject(FormBuilder);
  private readonly marketing = inject(MarketingService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(false);
  protected readonly sentTo = signal<string | null>(null);
  /** i18n key of the last server refusal. */
  protected readonly errorKey = signal<string | null>(null);
  protected readonly pricing = signal<PublicPricing | null>(null);
  /** The address was edited by hand: stop deriving it from the company name. */
  private subdomainTouched = false;

  protected readonly countryOptions = [{ value: '', label: '—' }, ...countryOptions('en')];

  protected readonly form = this.fb.nonNullable.group({
    planId: ['', [Validators.required]],
    companyName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    subdomain: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(25), Validators.pattern(SUBDOMAIN)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    country: ['', [Validators.required]],
    payrollTemplateId: [NO_TEMPLATE],
    /** Terms of Service and Data Processing Addendum; the workspace can't be created without them. */
    acceptTerms: [false, [Validators.requiredTrue]],
    website: [''],
  });

  protected readonly noTemplate = NO_TEMPLATE;
  private readonly planId = toSignal(this.form.controls.planId.valueChanges, { initialValue: '' });

  /** The chosen plan includes payroll (or the catalog does not gate it), so a payroll template can be chosen. */
  protected readonly planHasPayroll = computed(() => {
    const pricing = this.pricing();
    const payroll = pricing?.features.find(f => f.code === 'PAYROLL');
    if (!payroll) return true;
    const plan = pricing?.plans.find(p => p.id === (this.planId() || this.form.controls.planId.value));
    return !!plan?.items.some(i => i.featureId === payroll.id && i.included);
  });

  /** Templates for the chosen country; null while none is chosen or loading. */
  protected readonly templates = toSignal(
    this.form.controls.country.valueChanges.pipe(
      distinctUntilChanged(),
      switchMap(country =>
        country ? this.marketing.payrollTemplates(country).pipe(catchError(() => of<PublicPayrollTemplate[]>([]))) : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly planOptions = computed(() =>
    (this.pricing()?.plans ?? []).map(p => ({
      value: p.id,
      label: p.monthlyPrice > 0 ? `${p.name} — ${this.money(p.monthlyPrice)} / month after the trial` : p.name,
    })),
  );

  /** Live availability of the address; null while typing or when the format is wrong. */
  protected readonly check = toSignal(
    this.form.controls.subdomain.valueChanges.pipe(
      debounceTime(350),
      map(v => v.trim().toLowerCase()),
      distinctUntilChanged(),
      switchMap(v =>
        v.length >= 3 && SUBDOMAIN.test(v)
          ? this.marketing.checkSubdomain(v).pipe(catchError(() => of<SubdomainCheck | null>(null)))
          : of<SubdomainCheck | null>(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly subdomainUnavailable = computed(() => {
    const c = this.check();
    return !!c && !c.available;
  });

  constructor() {
    const planParam = this.route.snapshot.queryParamMap.get('plan');
    this.marketing
      .pricing()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: p => {
          this.pricing.set(p);
          if (p.plans.length === 0 || this.form.controls.planId.value) return;
          const wanted = p.plans.find(pl => pl.id === planParam) ?? p.plans.find(pl => pl.featured) ?? p.plans[0];
          this.form.controls.planId.setValue(wanted.id);
        },
        error: () => this.pricing.set(null),
      });
    const locale = /-([a-z]{2})$/i.exec(navigator.language);
    const region = locale ? locale[1].toUpperCase() : '';
    if (this.countryOptions.some(c => c.value === region)) this.form.controls.country.setValue(region);
    // A new country's templates replace the choice: suggest the first one (one click to choose none instead).
    toObservable(this.templates)
      .pipe(takeUntilDestroyed())
      .subscribe(list => this.form.controls.payrollTemplateId.setValue(list?.length ? list[0].id : NO_TEMPLATE));
    this.form.controls.companyName.valueChanges.pipe(takeUntilDestroyed()).subscribe(name => {
      if (!this.subdomainTouched) this.form.controls.subdomain.setValue(slugify(name));
    });
  }

  protected chooseTemplate(id: string): void {
    this.form.controls.payrollTemplateId.setValue(id);
  }

  /** "1 Jan 2026 – 31 Dec 2026" for a template's rules. */
  protected period(t: PublicPayrollTemplate): string {
    const format = (d: string): string =>
      new Intl.DateTimeFormat('en', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(d));
    return `${format(t.validFrom)} – ${format(t.validTo)}`;
  }

  protected onSubdomainInput(): void {
    this.subdomainTouched = true;
    const c = this.form.controls.subdomain;
    const clean = c.value.toLowerCase().replace(/[^a-z0-9-]/g, '');
    if (clean !== c.value) c.setValue(clean);
  }

  protected invalid(control: 'planId' | 'companyName' | 'subdomain' | 'email' | 'country'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected submit(): void {
    if (this.form.invalid || this.subdomainUnavailable()) {
      this.form.markAllAsTouched();
      return;
    }
    const f = this.form.getRawValue();
    this.loading.set(true);
    this.errorKey.set(null);
    this.marketing
      .startSignup({
        email: f.email.trim(),
        companyName: f.companyName.trim(),
        subdomain: f.subdomain.trim(),
        country: f.country,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        planId: f.planId,
        payrollTemplateId: this.planHasPayroll() && f.payrollTemplateId ? f.payrollTemplateId : undefined,
        acceptedTerms: f.acceptTerms,
        termsVersion: LEGAL_VERSION,
        website: f.website || undefined,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.sentTo.set(f.email.trim());
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.errorKey.set(signupErrorKey(err));
        },
      });
  }

  protected restart(): void {
    this.sentTo.set(null);
  }

  private money(amount: number): string {
    const currency = this.pricing()?.currency ?? 'USD';
    return new Intl.NumberFormat('en', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount);
  }
}

/** The register.* message for a refused sign-up call. */
export function signupErrorKey(err: HttpErrorResponse): string {
  if (err.status === 429) return 'register.errors.tooMany';
  const key = (err.error as { message?: string } | null)?.message?.replace(/^error\./, '');
  const known = [
    'disposableemail',
    'subdomaintaken',
    'subdomainreserved',
    'subdomaininvalid',
    'planunavailable',
    'payrolltemplateunavailable',
    'payrollnotinplan',
    'invalidlink',
    'linkexpired',
    'termsoutdated',
  ];
  return key && known.includes(key) ? `register.errors.${key}` : 'register.errors.generic';
}
