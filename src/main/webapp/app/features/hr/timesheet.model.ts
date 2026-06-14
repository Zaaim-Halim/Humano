import { AuditFields } from 'app/core/api';

/** `GET /api/hr/timesheets` (list) and `/{id}` (detail). */
export interface Timesheet extends AuditFields {
  id: string;
  employeeId: string;
  employeeName: string | null;
  date: string;
  /** Hours worked (decimal). */
  hoursWorked: number;
  projectId: string | null;
  projectName: string | null;
}

export interface CreateTimesheetRequest {
  employeeId: string;
  date: string;
  hoursWorked: number;
  projectId?: string;
}

export interface UpdateTimesheetRequest {
  date?: string;
  hoursWorked?: number;
  projectId?: string;
}

/** Criteria for `GET /api/hr/timesheets/search` (query params). */
export interface TimesheetSearchRequest {
  employeeId?: string;
  projectId?: string;
  dateFrom?: string;
  dateTo?: string;
  minHours?: number;
  maxHours?: number;
  createdBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
}
