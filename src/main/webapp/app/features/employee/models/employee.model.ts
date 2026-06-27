import { AuditFields } from 'app/core/api';

import { EmployeeStatus } from './enums/employee-status.enum';

/** List-row projection — `GET /api/hr/employees`. */
export interface SimpleEmployeeProfile {
  id: string;
  firstName: string | null;
  lastName: string | null;
  jobTitle: string | null;
  phone: string | null;
  status: EmployeeStatus;
  departmentName: string | null;
  positionName: string | null;
}

/** Display name for an employee row: "First Last", else job title, else id. */
export function personName(e: Pick<SimpleEmployeeProfile, 'id' | 'firstName' | 'lastName' | 'jobTitle'>): string {
  const name = `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim();
  return name ? name : (e.jobTitle ?? e.id);
}

/** Full profile — `GET /api/hr/employees/{id}`. Dates are ISO-8601 strings. */
export interface EmployeeProfile extends AuditFields {
  id: string;
  jobTitle: string | null;
  phone: string | null;
  startDate: string | null;
  endDate: string | null;
  status: EmployeeStatus;
  countryId: string | null;
  countryName: string | null;
  departmentId: string | null;
  departmentName: string | null;
  positionId: string | null;
  positionName: string | null;
  unitId: string | null;
  unitName: string | null;
  managerId: string | null;
  managerInfo: string | null;
  /** Granted role/authority names (includes the base EMPLOYEE role). */
  authorities: string[];
}

export interface CreateEmployeeProfileRequest {
  jobTitle?: string;
  phone?: string;
  /** Required (ISO date). */
  startDate: string;
  endDate?: string;
  status?: EmployeeStatus;
  countryId?: string;
  departmentId?: string;
  /** Required. */
  positionId: string;
  /** Required. */
  unitId: string;
  managerId?: string;
}

/**
 * One-step employee provisioning — `POST /api/hr/employees`. Creates the backing
 * user account (identity + roles) together with the HR profile; the recipient
 * sets their password via the emailed reset link. There is no separate user
 * management and no self-registration.
 */
export interface CreateEmployeeRequest extends CreateEmployeeProfileRequest {
  /** Required. Login/username for the account. */
  login: string;
  firstName?: string;
  lastName?: string;
  /** Required. Where the activation/creation email is sent. */
  email: string;
  imageUrl?: string;
  langKey?: string;
  /** Granted role/authority names (the EMPLOYEE role is always added server-side). */
  authorities?: string[];
}

/**
 * Partial profile update — all fields optional (`PUT /api/hr/employees/{id}`).
 * When `authorities` is provided it fully replaces the employee's roles.
 */
export type UpdateEmployeeProfileRequest = Partial<CreateEmployeeProfileRequest> & {
  authorities?: string[];
};

/** Criteria for `POST /api/hr/employees/search` (AND-combined, all optional). */
export interface EmployeeSearchRequest {
  /** Free-text term matched (OR) against first name, last name, and job title. */
  query?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  jobTitle?: string;
  phone?: string;
  status?: EmployeeStatus;
  departmentId?: string;
  positionId?: string;
  unitId?: string;
  managerId?: string;
  startDateFrom?: string;
  startDateTo?: string;
  endDateFrom?: string;
  endDateTo?: string;
}
