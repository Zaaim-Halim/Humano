import { AuditFields } from 'app/core/api';

/** EmployeeCertification — `/api/hr/employee-certifications`. */
export interface EmployeeCertification extends AuditFields {
  id: string;
  employeeId: string | null;
  name: string | null;
  issuer: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  verified: boolean;
  documentFileId: string | null;
}

export interface CreateEmployeeCertificationRequest {
  /** Required. */
  employeeId: string;
  name?: string;
  issuer?: string;
  issueDate?: string;
  expiryDate?: string;
  verified?: boolean;
  documentFileId?: string;
}

export interface UpdateEmployeeCertificationRequest {
  name?: string;
  issuer?: string;
  issueDate?: string;
  expiryDate?: string;
  verified?: boolean;
  documentFileId?: string;
}
