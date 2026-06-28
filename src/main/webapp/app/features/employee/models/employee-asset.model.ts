import { AuditFields } from 'app/core/api';

/** EmployeeAsset — `/api/hr/employee-assets`. */
export interface EmployeeAsset extends AuditFields {
  id: string;
  employeeId: string | null;
  type: string | null;
  identifier: string | null;
  assignedDate: string | null;
  returnedDate: string | null;
}

export interface CreateEmployeeAssetRequest {
  /** Required. */
  employeeId: string;
  type?: string;
  identifier?: string;
  assignedDate?: string;
  returnedDate?: string;
}

export interface UpdateEmployeeAssetRequest {
  type?: string;
  identifier?: string;
  assignedDate?: string;
  returnedDate?: string;
}
