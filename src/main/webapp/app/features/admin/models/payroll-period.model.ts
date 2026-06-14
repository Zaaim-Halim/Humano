/** `GET /api/payroll/periods/calendars/{calendarId}` etc. */
export interface PayrollPeriod {
  id: string;
  code: string;
  calendarId: string;
  calendarName: string | null;
  startDate: string;
  endDate: string;
  paymentDate: string | null;
  closed: boolean;
  payrollRunCount: number;
  hasApprovedRun: boolean;
}

export interface GeneratePayrollPeriodsRequest {
  calendarId: string;
  startDate: string;
  endDate: string;
  /** Days after period end the payment falls (>= 1). */
  paymentDayOffset: number;
  skipExistingPeriods?: boolean;
}
