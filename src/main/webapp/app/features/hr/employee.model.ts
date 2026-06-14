import { AuditFields } from 'app/core/api';

import { EmployeeStatus } from './enums/employee-status.enum';

/** List-row projection — `GET /api/hr/employees`. */
export interface SimpleEmployeeProfile {
  id: string;
  jobTitle: string | null;
  phone: string | null;
  status: EmployeeStatus;
  departmentName: string | null;
  positionName: string | null;
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

/** Partial update — all fields optional (`PUT /api/hr/employees/{id}`). */
export type UpdateEmployeeProfileRequest = Partial<CreateEmployeeProfileRequest>;

/** Criteria for `POST /api/hr/employees/search` (AND-combined, all optional). */
export interface EmployeeSearchRequest {
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
