import { AuditFields } from 'app/core/api';

import { EmployeeStatus } from './enums/employee-status.enum';

/**
 * Minimal embedded reference to a reference-data / country row. On reads it carries id + code +
 * name; on writes send just `{ id }` (a `RefInput`).
 */
export interface ReferenceDataRef {
  id: string;
  code: string | null;
  name: string | null;
}

/** Write-side reference: only the id is sent. */
export interface RefInput {
  id: string;
}

/** Nested personal / employment details. Used on reads and (partial) writes. */
export interface EmployeePersonalDetails {
  employeeNumber?: string | null;
  birthDate?: string | null;
  gender?: string | null;
  placeOfBirth?: string | null;
  workPhone?: string | null;
  workLocation?: string | null;
  fte?: number | null;
  probationEndDate?: string | null;
  confirmationDate?: string | null;
  terminationNotes?: string | null;
}

/** Nested government identification (sensitive). Update-only on the write side. */
export interface GovernmentIdentification {
  nationalId?: string | null;
  passportNumber?: string | null;
  taxNumber?: string | null;
  socialSecurityNumber?: string | null;
}

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
  // Personal / employment details (nested).
  personalDetails: EmployeePersonalDetails;
  // Government identification (nested, sensitive).
  governmentIds: GovernmentIdentification;
  // Reference-data relationships (nested).
  nationality: ReferenceDataRef | null;
  maritalStatus: ReferenceDataRef | null;
  employmentType: ReferenceDataRef | null;
  grade: ReferenceDataRef | null;
  level: ReferenceDataRef | null;
  category: ReferenceDataRef | null;
  terminationReason: ReferenceDataRef | null;
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
  // Personal / employment details (nested).
  personalDetails?: EmployeePersonalDetails;
  // Reference-data relationships (nested; send `{ id }`).
  nationality?: RefInput;
  maritalStatus?: RefInput;
  employmentType?: RefInput;
  grade?: RefInput;
  level?: RefInput;
  category?: RefInput;
  terminationReason?: RefInput;
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
 * Richer than create: provisioning is minimal, the rest of the profile is enriched here.
 * When `authorities` is provided it fully replaces the employee's roles.
 */
export type UpdateEmployeeProfileRequest = Partial<CreateEmployeeProfileRequest> & {
  authorities?: string[];
  // Government identification (nested, sensitive) — update-only.
  governmentIds?: GovernmentIdentification;
  // personalDetails and the reference-data relationships are inherited from CreateEmployeeProfileRequest.
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
