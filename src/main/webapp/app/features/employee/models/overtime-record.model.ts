import { AuditFields } from 'app/core/api';

import { OvertimeApprovalStatus } from './enums/overtime-approval-status.enum';
import { OvertimeType } from './enums/overtime-type.enum';

/** `GET /api/hr/overtime-records` (list) and `/{id}` (detail). */
export interface OvertimeRecord extends AuditFields {
  id: string;
  employeeId: string;
  employeeName: string | null;
  date: string;
  hours: number;
  type: OvertimeType;
  approvalStatus: OvertimeApprovalStatus;
  notes: string | null;
  approvedById: string | null;
  approvedByName: string | null;
}

export interface CreateOvertimeRecordRequest {
  employeeId: string;
  date: string;
  hours: number;
  type: OvertimeType;
  notes?: string;
}

/** Approve/reject — `PUT /api/hr/overtime-records/{id}/process`. */
export interface ProcessOvertimeRecordRequest {
  approvalStatus: OvertimeApprovalStatus;
  approvedById: string;
  notes?: string;
}

/** Criteria for `GET /api/hr/overtime-records/search` (query params). */
export interface OvertimeRecordSearchRequest {
  employeeId?: string;
  approvedById?: string;
  overtimeType?: OvertimeType;
  approvalStatus?: OvertimeApprovalStatus;
  dateFrom?: string;
  dateTo?: string;
  minHours?: number;
  maxHours?: number;
  notes?: string;
  createdBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
}
