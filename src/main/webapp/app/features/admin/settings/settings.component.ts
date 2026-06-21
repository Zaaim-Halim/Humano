import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import {
  Basis,
  Currency,
  CurrencyService,
  OrganizationSettings,
  OrganizationSettingsService,
  PayrollCalendar,
  PayrollCalendarService,
  UpdateOrganizationSettingsRequest,
} from '../index';

/**
 * Company settings (HR/admin) — the `/settings` screen over `GET/PUT /api/org-settings`
 * (P5.3). Edits the company-level payroll policy: standard hours, default basis,
 * default overtime multiplier, timezone, and optional default currency/calendar.
 * Reference pickers come from `CurrencyService` and `PayrollCalendarService`.
 */
@Component({
  selector: 'hum-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    CardComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    ButtonComponent,
    AlertComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './settings.component.html',
})
export default class SettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly settingsService = inject(OrganizationSettingsService);
  private readonly currencyService = inject(CurrencyService);
  private readonly calendarService = inject(PayrollCalendarService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  private readonly currencies = signal<Currency[]>([]);
  private readonly calendars = signal<PayrollCalendar[]>([]);
  protected readonly currencyOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.orgSettings.none') },
    ...this.currencies().map(c => ({ value: c.id, label: `${c.code} — ${c.name}` })),
  ]);
  protected readonly calendarOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.translate.instant('humano.orgSettings.none') },
    ...this.calendars().map(c => ({ value: c.id, label: c.name })),
  ]);
  protected readonly basisOptions: SelectOption[] = Object.values(Basis).map(b => ({ value: b, label: b }));

  protected readonly form = this.fb.nonNullable.group({
    standardHoursPerDay: ['', [Validators.required, Validators.min(0.1), Validators.max(24)]],
    standardHoursPerWeek: ['', [Validators.required, Validators.min(1), Validators.max(168)]],
    standardMonthlyHours: ['', [Validators.required, Validators.min(1), Validators.max(744)]],
    defaultBasis: [Basis.MONTHLY as Basis, Validators.required],
    defaultOvertimeMultiplier: ['', [Validators.required, Validators.min(1), Validators.max(5)]],
    timezone: ['', [Validators.required, Validators.maxLength(60)]],
    defaultCurrencyId: [''],
    defaultPayrollCalendarId: [''],
  });

  constructor() {
    this.load();
    this.loadReferenceData();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.settingsService.get().subscribe({
      next: s => {
        this.patch(s);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: UpdateOrganizationSettingsRequest = {
      standardHoursPerDay: Number(raw.standardHoursPerDay),
      standardHoursPerWeek: Number(raw.standardHoursPerWeek),
      standardMonthlyHours: Number(raw.standardMonthlyHours),
      defaultBasis: raw.defaultBasis,
      defaultOvertimeMultiplier: Number(raw.defaultOvertimeMultiplier),
      timezone: raw.timezone.trim(),
      ...(raw.defaultCurrencyId ? { defaultCurrencyId: raw.defaultCurrencyId } : {}),
      ...(raw.defaultPayrollCalendarId ? { defaultPayrollCalendarId: raw.defaultPayrollCalendarId } : {}),
    };

    this.saving.set(true);
    this.settingsService.update(body).subscribe({
      next: s => {
        this.toast.success(this.translate.instant('humano.orgSettings.saved'));
        this.patch(s);
        this.saving.set(false);
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  private patch(s: OrganizationSettings): void {
    this.form.reset({
      standardHoursPerDay: String(s.standardHoursPerDay),
      standardHoursPerWeek: String(s.standardHoursPerWeek),
      standardMonthlyHours: String(s.standardMonthlyHours),
      defaultBasis: s.defaultBasis,
      defaultOvertimeMultiplier: String(s.defaultOvertimeMultiplier),
      timezone: s.timezone,
      defaultCurrencyId: s.defaultCurrencyId ?? '',
      defaultPayrollCalendarId: s.defaultPayrollCalendarId ?? '',
    });
  }

  private loadReferenceData(): void {
    this.currencyService.list().subscribe({ next: res => this.currencies.set(res) });
    this.calendarService.active().subscribe({ next: res => this.calendars.set(res) });
  }
}
