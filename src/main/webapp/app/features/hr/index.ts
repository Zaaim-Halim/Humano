// Enums
export { EmployeeStatus } from './enums/employee-status.enum';
export { LeaveType } from './enums/leave-type.enum';
export { LeaveStatus } from './enums/leave-status.enum';
export { AttendanceStatus } from './enums/attendance-status.enum';
export { OvertimeType } from './enums/overtime-type.enum';
export { OvertimeApprovalStatus } from './enums/overtime-approval-status.enum';
export { OrganizationalUnitType } from './enums/organizational-unit-type.enum';

// People / HR (Phase 4.1)
export { EmployeeService } from './employee.service';
export type {
  SimpleEmployeeProfile,
  EmployeeProfile,
  CreateEmployeeProfileRequest,
  UpdateEmployeeProfileRequest,
  EmployeeSearchRequest,
} from './employee.model';

export { DepartmentService } from './department.service';
export type { Department, CreateDepartmentRequest, UpdateDepartmentRequest } from './department.model';

export { PositionService } from './position.service';
export type { Position, CreatePositionRequest, UpdatePositionRequest } from './position.model';

export { OrganizationalUnitService } from './organizational-unit.service';
export type { OrganizationalUnit, CreateOrganizationalUnitRequest, UpdateOrganizationalUnitRequest } from './organizational-unit.model';

export { EmployeeDocumentService } from './employee-document.service';
export type { EmployeeDocument, CreateEmployeeDocumentRequest, UpdateEmployeeDocumentRequest } from './employee-document.model';

// Time & Leave (Phase 4.2) — backend-co-located under /api/hr
export { LeaveRequestService } from './leave-request.service';
export type { LeaveRequest, CreateLeaveRequest, ProcessLeaveRequest, LeaveRequestSearchRequest } from './leave-request.model';

export { TimesheetService } from './timesheet.service';
export type { Timesheet, CreateTimesheetRequest, UpdateTimesheetRequest, TimesheetSearchRequest } from './timesheet.model';

export { AttendanceService } from './attendance.service';
export type {
  Attendance,
  AttendanceEvent,
  CreateAttendanceRequest,
  UpdateAttendanceRequest,
  AttendanceSearchRequest,
} from './attendance.model';

export { OvertimeRecordService } from './overtime-record.service';
export type {
  OvertimeRecord,
  CreateOvertimeRecordRequest,
  ProcessOvertimeRecordRequest,
  OvertimeRecordSearchRequest,
} from './overtime-record.model';

// Performance (Phase 4.4)
export { PerformanceReviewService } from './performance-review.service';
export type {
  PerformanceReview,
  CreatePerformanceReviewRequest,
  UpdatePerformanceReviewRequest,
  PerformanceReviewSearchRequest,
} from './performance-review.model';
