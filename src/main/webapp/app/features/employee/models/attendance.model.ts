import { AuditFields } from 'app/core/api';

import { AttendanceStatus } from './enums/attendance-status.enum';

/** A check-in/out event within an attendance record (`AttendanceEventResponse`). */
export interface AttendanceEvent {
  id: string;
  eventType: string;
  /** ISO time `HH:mm:ss`. */
  eventTime: string | null;
  eventAction: string;
  description: string | null;
}

/** `GET /api/hr/attendances` (list) and `/{id}` (detail). Times are `HH:mm:ss`. */
export interface Attendance extends AuditFields {
  id: string;
  employeeId: string;
  employeeName: string | null;
  date: string;
  checkIn: string | null;
  checkOut: string | null;
  status: AttendanceStatus;
  events: AttendanceEvent[];
}

export interface CreateAttendanceRequest {
  employeeId: string;
  date: string;
  checkIn?: string;
  checkOut?: string;
  status: AttendanceStatus;
}

export interface UpdateAttendanceRequest {
  checkIn?: string;
  checkOut?: string;
  status?: AttendanceStatus;
}

/** Criteria for `GET /api/hr/attendances/search` (query params). */
export interface AttendanceSearchRequest {
  employeeId?: string;
  startDate?: string;
  endDate?: string;
  status?: AttendanceStatus;
  checkInFrom?: string;
  checkInTo?: string;
  checkOutFrom?: string;
  checkOutTo?: string;
  createdBy?: string;
  lastModifiedBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
}
