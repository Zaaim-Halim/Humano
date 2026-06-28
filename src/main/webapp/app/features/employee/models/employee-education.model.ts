import { AuditFields } from 'app/core/api';

/** EmployeeEducation — `/api/hr/employee-educations`. */
export interface EmployeeEducation extends AuditFields {
  id: string;
  employeeId: string | null;
  institution: string | null;
  degree: string | null;
  fieldOfStudy: string | null;
  graduationDate: string | null;
  documentFileId: string | null;
}

export interface CreateEmployeeEducationRequest {
  /** Required. */
  employeeId: string;
  institution?: string;
  degree?: string;
  fieldOfStudy?: string;
  graduationDate?: string;
  documentFileId?: string;
}

export interface UpdateEmployeeEducationRequest {
  institution?: string;
  degree?: string;
  fieldOfStudy?: string;
  graduationDate?: string;
  documentFileId?: string;
}
