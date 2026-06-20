// Employee persona — per-person HR records (profile, leave, time, attendance, overtime, performance, documents).
// Services live here once; Admin/Manager surfaces import them across personas.

// Enums
export { EmployeeStatus } from './models/enums/employee-status.enum';
export { LeaveType } from './models/enums/leave-type.enum';
export { LeaveStatus } from './models/enums/leave-status.enum';
export { AttendanceStatus } from './models/enums/attendance-status.enum';
export { OvertimeType } from './models/enums/overtime-type.enum';
export { OvertimeApprovalStatus } from './models/enums/overtime-approval-status.enum';

// Self-service identity → employee resolution seam
export { CurrentEmployeeService } from './services/current-employee.service';

// Employee profile
export { EmployeeService } from './services/employee.service';
export type {
  SimpleEmployeeProfile,
  EmployeeProfile,
  CreateEmployeeProfileRequest,
  UpdateEmployeeProfileRequest,
  EmployeeSearchRequest,
} from './models/employee.model';

// Leave
export { LeaveRequestService } from './services/leave-request.service';
export type { LeaveRequest, CreateLeaveRequest, ProcessLeaveRequest, LeaveRequestSearchRequest } from './models/leave-request.model';

// Time & attendance
export { TimesheetService } from './services/timesheet.service';
export type { Timesheet, CreateTimesheetRequest, UpdateTimesheetRequest, TimesheetSearchRequest } from './models/timesheet.model';

export { AttendanceService } from './services/attendance.service';
export type {
  Attendance,
  AttendanceEvent,
  CreateAttendanceRequest,
  UpdateAttendanceRequest,
  AttendanceSearchRequest,
} from './models/attendance.model';

export { OvertimeRecordService } from './services/overtime-record.service';
export type {
  OvertimeRecord,
  CreateOvertimeRecordRequest,
  ProcessOvertimeRecordRequest,
  OvertimeRecordSearchRequest,
} from './models/overtime-record.model';

// Performance
export { PerformanceReviewService } from './services/performance-review.service';
export type {
  PerformanceReview,
  CreatePerformanceReviewRequest,
  UpdatePerformanceReviewRequest,
  PerformanceReviewSearchRequest,
} from './models/performance-review.model';

// Documents
export { EmployeeDocumentService } from './services/employee-document.service';
export type { EmployeeDocument, CreateEmployeeDocumentRequest, UpdateEmployeeDocumentRequest } from './models/employee-document.model';

// Custom attributes
export { EmployeeAttributeService } from './services/employee-attribute.service';
export type { EmployeeAttribute, UpdateEmployeeAttributesRequest } from './models/employee-attribute.model';

// Hierarchy (People Tree and Organization Tree)
export { HrHierarchyService } from './services/hr-hierarchy.service';
export type {
  EmployeeRef,
  OrganizationalUnitRef,
  EmployeeTreeNode,
  EmployeeHierarchyResponse,
  OrganizationalUnitTreeNode,
  OrganizationalUnitHierarchyResponse,
  HierarchyAncestorsResponse,
} from './models/hierarchy.model';

// Tree Components
export { PeopleTreeComponent } from './people-tree/people-tree.component';
export { EmployeeTreeNodeComponent } from './people-tree/employee-tree-node/employee-tree-node.component';
export { OrganizationTreeComponent } from './organization-tree/organization-tree.component';
export { OrganizationalUnitNodeComponent } from './organization-tree/organizational-unit-node/organizational-unit-node.component';
