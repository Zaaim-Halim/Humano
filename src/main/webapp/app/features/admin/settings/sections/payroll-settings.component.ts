import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { CompanyService } from 'app/core/company/company.service';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  ToastService,
} from 'app/shared/ui';

import { Basis, OrganizationSettings, OrganizationSettingsService, PayrollCalendar, PayrollCalendarService } from '../../index';
import { CreatePayrollCalendarRequest } from '../../models/payroll-calendar.model';

/**
 * Payroll & time — the rules payroll and time tracking run on:
 *
 * - Working time: standard hours per day / week / month (the month figure converts hourly pay)
 *   and the overtime multiplier.
 * - Pay defaults: the pay basis new compensations start from and the default payroll calendar.
 * - Payroll calendars: each active calendar with its frequency and next periods; create one, or
 *   switch one off (with confirmation — inactive calendars stop generating periods).
 * Pay rules have their own screen, linked from here.
 */
@Component({
  selector: 'hum-payroll-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    DrawerComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './payroll-settings.component.html',
})
export class PayrollSettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly settingsService = inject(OrganizationSettingsService);
  private readonly calendarService = inject(PayrollCalendarService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly company = inject(CompanyService);

  protected readonly settings = signal<OrganizationSettings | null>(null);
  protected readonly calendars = signal<PayrollCalendar[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    standardHoursPerDay: ['', [Validators.required, Validators.min(0.1), Validators.max(24)]],
    standardHoursPerWeek: ['', [Validators.required, Validators.min(1), Validators.max(168)]],
    standardMonthlyHours: ['', [Validators.required, Validators.min(1), Validators.max(744)]],
    defaultOvertimeMultiplier: ['', [Validators.required, Validators.min(1), Validators.max(5)]],
    defaultBasis: [Basis.MONTHLY as Basis, Validators.required],
    defaultPayrollCalendarId: [''],
  });

  protected readonly basisOptions = computed<SelectOption[]>(() =>
    Object.values(Basis).map(b => ({ value: b, label: this.t(`humano.insights.pay.basis.${b}`) })),
  );
  protected readonly calendarOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.t('humano.settings.payroll.noCalendar') },
    ...this.calendars().map(c => ({ value: c.id, label: c.name })),
  ]);
  protected readonly frequencyOptions: SelectOption[] = ['MONTHLY', 'SEMIMONTHLY', 'BIWEEKLY', 'WEEKLY'].map(f => ({
    value: f,
    label: f,
  }));

  // New calendar / switch off
  protected readonly calendarFormOpen = signal(false);
  protected readonly calendarSaving = signal(false);
  protected readonly calendarForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(255)]],
    frequency: ['MONTHLY' as CreatePayrollCalendarRequest['frequency'], Validators.required],
  });
  protected readonly deactivateTarget = signal<PayrollCalendar | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    forkJoin({ settings: this.settingsService.get(), calendars: this.calendarService.active() }).subscribe({
      next: ({ settings, calendars }) => {
        this.settings.set(settings);
        this.calendars.set(calendars);
        this.form.reset({
          standardHoursPerDay: String(settings.standardHoursPerDay),
          standardHoursPerWeek: String(settings.standardHoursPerWeek),
          standardMonthlyHours: String(settings.standardMonthlyHours),
          defaultOvertimeMultiplier: String(settings.defaultOvertimeMultiplier),
          defaultBasis: settings.defaultBasis,
          defaultPayrollCalendarId: settings.defaultPayrollCalendarId ?? '',
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
    this.settingsService
      .update({
        standardHoursPerDay: Number(raw.standardHoursPerDay),
        standardHoursPerWeek: Number(raw.standardHoursPerWeek),
        standardMonthlyHours: Number(raw.standardMonthlyHours),
        defaultOvertimeMultiplier: Number(raw.defaultOvertimeMultiplier),
        defaultBasis: raw.defaultBasis,
        timezone: s.timezone,
        ...(s.defaultCurrencyId ? { defaultCurrencyId: s.defaultCurrencyId } : {}),
        ...(raw.defaultPayrollCalendarId ? { defaultPayrollCalendarId: raw.defaultPayrollCalendarId } : {}),
      })
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

  /** "Monthly hours" suggestion: weekly hours × 52 / 12. */
  protected suggestedMonthly(): number {
    return Math.round((Number(this.form.controls.standardHoursPerWeek.value) * 52) / 12);
  }

  protected openCalendarForm(): void {
    this.calendarForm.reset({ name: '', frequency: 'MONTHLY' });
    this.calendarFormOpen.set(true);
  }

  protected createCalendar(): void {
    if (this.calendarForm.invalid) {
      this.calendarForm.markAllAsTouched();
      return;
    }
    const raw = this.calendarForm.getRawValue();
    this.calendarSaving.set(true);
    this.calendarService
      .create({ name: raw.name.trim(), frequency: raw.frequency, timezone: this.settings()?.timezone ?? 'UTC', active: true })
      .subscribe({
        next: () => {
          this.calendarSaving.set(false);
          this.calendarFormOpen.set(false);
          this.toast.success(this.t('humano.settings.payroll.calendarCreated'));
          this.calendarService.active().subscribe(list => this.calendars.set(list));
        },
        error: (err: unknown) => {
          this.calendarSaving.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  protected confirmDeactivate(): void {
    const c = this.deactivateTarget();
    if (!c) return;
    this.calendarService.setActive(c.id, false).subscribe({
      next: () => {
        this.deactivateTarget.set(null);
        this.calendars.update(list => list.filter(x => x.id !== c.id));
        this.toast.success(this.t('humano.settings.payroll.calendarOff'));
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
