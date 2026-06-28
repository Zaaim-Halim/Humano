import { AuditFields } from 'app/core/api';

/** EmploymentContract — `/api/hr/employment-contracts`. */
export interface EmploymentContract extends AuditFields {
  id: string;
  employeeId: string | null;
  contractNumber: string | null;
  startDate: string | null;
  endDate: string | null;
  contractType: string | null;
  positionId: string | null;
  departmentId: string | null;
  workingHours: number | null;
  signedDate: string | null;
  status: string | null;
}

export interface CreateEmploymentContractRequest {
  /** Required. */
  employeeId: string;
  contractNumber?: string;
  startDate?: string;
  endDate?: string;
  contractType?: string;
  positionId?: string;
  departmentId?: string;
  workingHours?: number;
  signedDate?: string;
  status?: string;
}

export interface UpdateEmploymentContractRequest {
  contractNumber?: string;
  startDate?: string;
  endDate?: string;
  contractType?: string;
  positionId?: string;
  departmentId?: string;
  workingHours?: number;
  signedDate?: string;
  status?: string;
}
