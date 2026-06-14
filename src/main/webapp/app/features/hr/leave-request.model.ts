import { AuditFields } from 'app/core/api';

import { LeaveStatus } from './enums/leave-status.enum';
import { LeaveType } from './enums/leave-type.enum';

/** `GET /api/hr/leave-requests` (list) and `/{id}` (detail) — same shape. */
export interface LeaveRequest extends AuditFields {
  id: string;
  startDate: string;
  endDate: string;
  leaveType: LeaveType;
  status: LeaveStatus;
  reason: string | null;
  daysCount: number | null;
  employeeId: string;
  employeeName: string | null;
  approverId: string | null;
  approverName: string | null;
  approverComments: string | null;
}

export interface CreateLeaveRequest {
  /** Required (ISO date). */
  startDate: string;
  /** Required (ISO date). */
  endDate: string;
  /** Required. */
  leaveType: LeaveType;
  /** Required, 20–1000 chars. */
  reason: string;
  /** Required. */
  employeeId: string;
}

/** Approve/reject — `PUT /api/hr/leave-requests/{id}/process`. */
export interface ProcessLeaveRequest {
  status: LeaveStatus;
  approverComments?: string;
  /** Required. */
  approverId: string;
}

/** Criteria for `GET /api/hr/leave-requests/search` (query params, all optional). */
export interface LeaveRequestSearchRequest {
  employeeId?: string;
  approverId?: string;
  leaveType?: LeaveType;
  status?: LeaveStatus;
  startDateFrom?: string;
  startDateTo?: string;
  endDateFrom?: string;
  endDateTo?: string;
  reason?: string;
  minDaysCount?: number;
  maxDaysCount?: number;
  createdBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
}
