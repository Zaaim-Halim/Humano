import { AuditFields } from 'app/core/api';

/** EmployeeExperience — `/api/hr/employee-experiences`. */
export interface EmployeeExperience extends AuditFields {
  id: string;
  employeeId: string | null;
  company: string | null;
  position: string | null;
  startDate: string | null;
  endDate: string | null;
}

export interface CreateEmployeeExperienceRequest {
  /** Required. */
  employeeId: string;
  company?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
}

export interface UpdateEmployeeExperienceRequest {
  company?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
}
