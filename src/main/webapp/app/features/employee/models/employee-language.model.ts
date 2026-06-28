import { AuditFields } from 'app/core/api';

/** EmployeeLanguage — `/api/hr/employee-languages`. */
export interface EmployeeLanguage extends AuditFields {
  id: string;
  employeeId: string | null;
  language: string | null;
  reading: string | null;
  writing: string | null;
  speaking: string | null;
}

export interface CreateEmployeeLanguageRequest {
  /** Required. */
  employeeId: string;
  language?: string;
  reading?: string;
  writing?: string;
  speaking?: string;
}

export interface UpdateEmployeeLanguageRequest {
  language?: string;
  reading?: string;
  writing?: string;
  speaking?: string;
}
