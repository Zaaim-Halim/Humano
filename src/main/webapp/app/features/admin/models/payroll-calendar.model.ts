/** A period summary embedded in {@link PayrollCalendar} (backend `PayrollPeriodSummary`). */
export interface PayrollPeriodSummary {
  id: string;
  code: string;
  startDate: string;
  endDate: string;
  paymentDate: string | null;
  closed: boolean;
}

/** `GET /api/payroll/calendars/active` — active calendars with their upcoming periods. */
export interface PayrollCalendar {
  id: string;
  name: string;
  frequency: string;
  timezone: string | null;
  active: boolean;
  periodCount: number;
  upcomingPeriods: PayrollPeriodSummary[];
}

/** `POST /api/payroll/calendars`. */
export interface CreatePayrollCalendarRequest {
  name: string;
  frequency: 'MONTHLY' | 'SEMIMONTHLY' | 'BIWEEKLY' | 'WEEKLY';
  timezone: string;
  active: boolean;
}
