import { TranslateService } from '@ngx-translate/core';

import { EmployeeProfile } from '../models/employee.model';

/** "First Last", falling back to the job title for accounts without a name. */
export function employeeFullName(e: EmployeeProfile): string {
  return [e.firstName, e.lastName].filter(Boolean).join(' ') || (e.jobTitle ?? '—');
}

/** Whole years and months since an ISO date, e.g. "2 yr 3 mo". */
export function tenureSince(isoDate: string | null, translate: TranslateService): string {
  if (!isoDate) return '';
  const from = new Date(`${isoDate}T00:00:00`);
  const now = new Date();
  let months = (now.getFullYear() - from.getFullYear()) * 12 + now.getMonth() - from.getMonth();
  if (now.getDate() < from.getDate()) months--;
  if (months < 1) return String(translate.instant('humano.employee360.tenureNew'));
  const years = Math.floor(months / 12);
  return String(
    translate.instant(years ? 'humano.employee360.tenureYears' : 'humano.employee360.tenureMonths', { years, months: months % 12 }),
  );
}

/** Age in whole years from an ISO birth date. */
export function ageFrom(isoDate: string | null): number | null {
  if (!isoDate) return null;
  const b = new Date(`${isoDate}T00:00:00`);
  const now = new Date();
  return now.getFullYear() - b.getFullYear() - (now < new Date(now.getFullYear(), b.getMonth(), b.getDate()) ? 1 : 0);
}
