import { Basis } from './enums/basis.enum';

/** `GET/PUT /api/org-settings` — company-level payroll policy. `id` is null until first saved. */
export interface OrganizationSettings {
  id: string | null;
  standardHoursPerDay: number;
  standardHoursPerWeek: number;
  standardMonthlyHours: number;
  defaultBasis: Basis;
  defaultOvertimeMultiplier: number;
  timezone: string;
  defaultCurrencyId: string | null;
  defaultCurrencyCode: string | null;
  defaultPayrollCalendarId: string | null;
  defaultPayrollCalendarName: string | null;
}

export interface UpdateOrganizationSettingsRequest {
  standardHoursPerDay: number;
  standardHoursPerWeek: number;
  standardMonthlyHours: number;
  defaultBasis: Basis;
  defaultOvertimeMultiplier: number;
  timezone: string;
  defaultCurrencyId?: string;
  defaultPayrollCalendarId?: string;
}
