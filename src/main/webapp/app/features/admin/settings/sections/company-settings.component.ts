import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin, switchMap } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { CompanyService } from 'app/core/company/company.service';
import { Tenant } from 'app/features/platform/models/tenant.model';
import { CurrentTenantService } from 'app/features/platform/services/current-tenant.service';
import {
  AlertComponent,
  ButtonComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { Currency, CurrencyService, OrganizationSettings, OrganizationSettingsService } from '../../index';

/** Currencies listed first in the picker. */
const COMMON = ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'CHF', 'JPY', 'MAD'];

/** "Europe/Paris" → "(UTC+02:00) Europe/Paris" for the current date. */
function zoneLabel(zone: string): string {
  try {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone: zone, timeZoneName: 'longOffset' }).formatToParts(new Date());
    const offset = parts.find(p => p.type === 'timeZoneName')?.value.replace('GMT', 'UTC') ?? 'UTC';
    return `(${offset === 'UTC' ? 'UTC+00:00' : offset}) ${zone.replace(/_/g, ' ')}`;
  } catch {
    return zone;
  }
}

/**
 * Company — who the company is and where it runs: name, website domain, workspace address
 * (read-only), timezone and default currency.
 *
 * The timezone is stored twice in the backend (the tenant record seeds new calendars; the
 * organization settings drive the time clock and payroll), so saving writes both and they never
 * drift. Currency and timezone pickers list common choices first.
 */
@Component({
  selector: 'hum-company-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './company-settings.component.html',
})
export class CompanySettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly tenantService = inject(CurrentTenantService);
  private readonly settingsService = inject(OrganizationSettingsService);
  private readonly currencyService = inject(CurrencyService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly company = inject(CompanyService);

  protected readonly tenant = signal<Tenant | null>(null);
  private readonly settings = signal<OrganizationSettings | null>(null);
  private readonly currencies = signal<Currency[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    domain: ['', [Validators.pattern(/^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}$/)]],
    timezone: ['', Validators.required],
    defaultCurrencyId: [''],
  });

  protected readonly timezoneOptions = computed<SelectOption[]>(() => {
    const zones: string[] = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.('timeZone') ?? ['UTC'];
    const current = this.settings()?.timezone;
    const all = current && !zones.includes(current) ? [current, ...zones] : zones;
    return all.map(z => ({ value: z, label: zoneLabel(z) }));
  });
  protected readonly currencyOptions = computed<SelectOption[]>(() => {
    const list = [...this.currencies()];
    const rank = (c: Currency): number => (COMMON.includes(c.code) ? COMMON.indexOf(c.code) : 100);
    list.sort((a, b) => rank(a) - rank(b) || a.code.localeCompare(b.code));
    return [
      { value: '', label: this.t('humano.settings.company.noCurrency') },
      ...list.map(c => ({ value: c.id, label: `${c.code} — ${c.name}` })),
    ];
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    forkJoin({
      tenant: this.tenantService.current(),
      settings: this.settingsService.get(),
      currencies: this.currencyService.list(),
    }).subscribe({
      next: ({ tenant, settings, currencies }) => {
        this.tenant.set(tenant);
        this.settings.set(settings);
        this.currencies.set(currencies);
        this.form.reset({
          name: tenant.name ?? '',
          domain: tenant.domain ?? '',
          timezone: settings.timezone ?? 'UTC',
          defaultCurrencyId: settings.defaultCurrencyId ?? '',
        });
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected save(): void {
    const s = this.settings();
    if (this.form.invalid || !s) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.saving.set(true);
    this.tenantService
      .updateCurrent({ name: raw.name.trim(), ...(raw.domain.trim() ? { domain: raw.domain.trim() } : {}), timezone: raw.timezone })
      .pipe(
        switchMap(tenant => {
          this.tenant.set(tenant);
          return this.settingsService.update({
            standardHoursPerDay: s.standardHoursPerDay,
            standardHoursPerWeek: s.standardHoursPerWeek,
            standardMonthlyHours: s.standardMonthlyHours,
            defaultBasis: s.defaultBasis,
            defaultOvertimeMultiplier: s.defaultOvertimeMultiplier,
            timezone: raw.timezone,
            ...(raw.defaultCurrencyId ? { defaultCurrencyId: raw.defaultCurrencyId } : {}),
            ...(s.defaultPayrollCalendarId ? { defaultPayrollCalendarId: s.defaultPayrollCalendarId } : {}),
          });
        }),
      )
      .subscribe({
        next: settings => {
          this.settings.set(settings);
          this.saving.set(false);
          this.form.markAsPristine();
          this.company.refresh(); // reflect the change across the app right away
          this.toast.success(this.t('humano.settings.saved'));
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
