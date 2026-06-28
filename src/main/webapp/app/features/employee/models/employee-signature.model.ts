import { AuditFields } from 'app/core/api';

/** EmployeeSignature — `/api/hr/employee-signatures`. */
export interface EmployeeSignature extends AuditFields {
  id: string;
  employeeId: string | null;
  signatureFileId: string | null;
  certificate: string | null;
}

export interface CreateEmployeeSignatureRequest {
  /** Required. */
  employeeId: string;
  signatureFileId?: string;
  certificate?: string;
}

export interface UpdateEmployeeSignatureRequest {
  signatureFileId?: string;
  certificate?: string;
}
