import { AuditFields } from 'app/core/api';

/** EmployeeLicense — `/api/hr/employee-licenses`. */
export interface EmployeeLicense extends AuditFields {
  id: string;
  employeeId: string | null;
  name: string | null;
  issuer: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  verified: boolean;
  documentFileId: string | null;
}

export interface CreateEmployeeLicenseRequest {
  /** Required. */
  employeeId: string;
  name?: string;
  issuer?: string;
  issueDate?: string;
  expiryDate?: string;
  verified?: boolean;
  documentFileId?: string;
}

export interface UpdateEmployeeLicenseRequest {
  name?: string;
  issuer?: string;
  issueDate?: string;
  expiryDate?: string;
  verified?: boolean;
  documentFileId?: string;
}
