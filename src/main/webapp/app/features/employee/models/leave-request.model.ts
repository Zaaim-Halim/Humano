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

/**
 * `LeaveCalendarEntry` — one absence on the shared team calendar. No reason or approver;
 * `leaveType` is null when the viewer may not see it (a colleague's leave, for non-HR).
 */
export interface LeaveCalendarEntry {
  id: string;
  employeeId: string;
  employeeName: string;
  imageUrl: string | null;
  departmentName: string | null;
  startDate: string;
  endDate: string;
  daysCount: number | null;
  status: LeaveStatus;
  leaveType: LeaveType | null;
}
