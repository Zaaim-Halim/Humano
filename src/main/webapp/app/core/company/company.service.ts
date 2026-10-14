import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

/** `GET /api/me/company` — the company settings every screen reflects. */
export interface CompanyProfile {
  name: string;
  logo: string | null;
  timezone: string;
  defaultCurrencyId: string | null;
  currencyCode: string | null;
  standardHoursPerDay: number;
  standardHoursPerWeek: number;
  standardMonthlyHours: number;
  overtimeMultiplier: number;
  defaultBasis: 'MONTHLY' | 'ANNUAL' | 'HOURLY';
}

/**
 * The company's settings as the whole app sees them (Settings → Company / Payroll & time):
 * name (shell, payslips), timezone, default currency (money formatting, new salaries), working
 * hours (timesheet and time-clock targets) and default pay basis.
 *
 * Loaded once after sign-in and cached in signals with sensible fallbacks, so screens can read
 * it synchronously; Settings calls {@link refresh} after saving so the change shows everywhere
 * without a reload.
 */
@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);
  private readonly url = inject(ApplicationConfigService).getEndpointFor('api/me/company');
  private loading = false;

  readonly profile = signal<CompanyProfile | null>(null);

  readonly name = computed(() => this.profile()?.name ?? '');
  readonly currency = computed(() => this.profile()?.currencyCode ?? 'USD');
  readonly timezone = computed(() => this.profile()?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone);
  readonly hoursPerDay = computed(() => Number(this.profile()?.standardHoursPerDay ?? 8));
  readonly hoursPerWeek = computed(() => Number(this.profile()?.standardHoursPerWeek ?? 40));
  /** Working days per week implied by the policy (e.g. 40 h ÷ 8 h = 5). */
  readonly workDaysPerWeek = computed(() => Math.max(1, Math.min(7, Math.round(this.hoursPerWeek() / this.hoursPerDay()))));

  /** Loads the profile once (no-op when already loaded or in flight). */
  load(): void {
    if (this.profile() || this.loading) return;
    this.refresh();
  }

  /** Re-reads the profile — after Settings are saved. */
  refresh(): void {
    this.loading = true;
    this.http.get<CompanyProfile>(this.url).subscribe({
      next: p => {
        this.profile.set(p);
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  /** Forget the profile (sign-out / tenant switch). */
  clear(): void {
    this.profile.set(null);
  }
}
