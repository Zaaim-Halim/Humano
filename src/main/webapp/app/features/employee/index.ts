// Employee persona — per-person HR records (profile, leave, time, attendance, overtime, performance, documents).
// Services live here once; Admin/Manager surfaces import them across personas.

// Enums
export { EmployeeStatus } from './models/enums/employee-status.enum';
export { WorkLocationType } from './models/enums/work-location-type.enum';
export { LEAVE_TYPE_COLOR, LEAVE_TYPE_ICON, LeaveType } from './models/enums/leave-type.enum';
export { LeaveStatus } from './models/enums/leave-status.enum';
export { AttendanceStatus } from './models/enums/attendance-status.enum';
export { OvertimeType } from './models/enums/overtime-type.enum';
export { OvertimeApprovalStatus } from './models/enums/overtime-approval-status.enum';

// Self-service identity → employee resolution seam
export { CurrentEmployeeService } from './services/current-employee.service';

// Employee profile
export { EmployeeService } from './services/employee.service';
export { personName } from './models/employee.model';
export type {
  SimpleEmployeeProfile,
  EmployeeProfile,
  CreateEmployeeProfileRequest,
  CreateEmployeeRequest,
  UpdateEmployeeProfileRequest,
  EmployeeSearchRequest,
  EmployeeDirectoryStats,
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
  PerformanceReviewStatus,
  SelfAssessment,
  ManagerAssessment,
  ReviewPotential,
  ProbationRecommendation,
  ReviewAcknowledgement,
  ReviewExclusion,
  ReviewSignOff,
  ReviewSignOffDetail,
  SignOffStatus,
} from './models/performance-review.model';

export { ReviewCycleService } from './services/review-cycle.service';
export type { ReviewPhaseStep } from './services/review-cycle.service';
export { ReviewCalendarService } from './services/review-calendar.service';
export { PerformanceFrameworkService } from './services/performance-framework.service';
export { COMPETENCY_CATEGORIES, REVIEW_QUESTION_AUDIENCES } from './models/performance-framework.model';
export { ImprovementPlanService } from './services/improvement-plan.service';
export { IMPROVEMENT_PLAN_LIMITS, IMPROVEMENT_PLAN_OUTCOMES, IMPROVEMENT_PLAN_PROGRESS } from './models/improvement-plan.model';
export type {
  ImprovementPlan,
  ImprovementPlanActions,
  ImprovementPlanCheckIn,
  ImprovementPlanObjective,
  ImprovementPlanOutcome,
  ImprovementPlanProgress,
  ImprovementPlanStatus,
  ReviewImprovementPlanStatus,
  SaveImprovementPlanRequest,
} from './models/improvement-plan.model';
export { GoalService } from './services/goal.service';
export { GOAL_ACHIEVEMENT_SCORE, GOAL_ACHIEVEMENTS, GOAL_STATUSES } from './models/goal.model';
export type {
  Goal,
  GoalAchievement,
  GoalActions,
  GoalCheckIn,
  GoalCheckInRequest,
  GoalRatingInput,
  GoalStatus,
  GoalTeamMember,
  ReviewFormGoal,
  SaveGoalRequest,
} from './models/goal.model';
export type {
  Competency,
  CompetencyCategory,
  CompetencyRatingInput,
  ReviewAnswerInput,
  ReviewForm,
  ReviewFormCompetency,
  ReviewFormQuestion,
  ReviewQuestionAudience,
  ReviewTemplate,
  ReviewTemplateQuestion,
  SaveCompetencyRequest,
  SaveReviewTemplateRequest,
} from './models/performance-framework.model';
export { FEEDBACK_VISIBILITIES, RATING_SCALE, REVIEW_CYCLE_PHASES, REVIEW_CYCLE_TYPES } from './models/review-cycle.model';
export { PROBATION_RECOMMENDATIONS, REVIEW_POTENTIALS } from './models/performance-review.model';
export type {
  ReviewCyclePhase,
  ReviewCycle,
  ReviewCycleEmployee,
  ReviewCycleType,
  ReviewCycleProgress,
  ReviewDepartmentProgress,
  ReviewPhaseDeadlines,
  SaveReviewCycleRequest,
  SelfAssessmentRequest,
  ManagerReviewRequest,
  CalibrateReviewRequest,
  AcknowledgeReviewRequest,
  CycleCandidate,
  MoveBackPhaseRequest,
  ResolveDisputeRequest,
  RatingChange,
  RatingChangeSource,
  ReviewCadence,
  ReviewCalendar,
  ReviewCycleProposal,
  UpdateReviewCalendarRequest,
  FeedbackVisibility,
  ReviewFeedbackSettings,
} from './models/review-cycle.model';

export { ReviewFeedbackService } from './services/review-feedback.service';
export { FEEDBACK_RELATIONSHIPS } from './models/review-feedback.model';
export type {
  FeedbackCandidate,
  FeedbackRelationship,
  FeedbackRequestStatus,
  ReviewFeedback,
  ReviewFeedbackOverview,
  ReviewFeedbackCount,
  RequestFeedbackRequest,
  SubmitFeedbackRequest,
} from './models/review-feedback.model';

// Documents

export { EmployeeDocumentService } from './services/employee-document.service';
export type { EmployeeDocument, CreateEmployeeDocumentRequest, UpdateEmployeeDocumentRequest } from './models/employee-document.model';

// Custom attributes
export { EmployeeAttributeService } from './services/employee-attribute.service';
export type { EmployeeAttribute, UpdateEmployeeAttributesRequest } from './models/employee-attribute.model';

// Addresses
export { AddressService } from './services/address.service';
export type { Address, CreateAddressRequest, UpdateAddressRequest } from './models/address.model';

// Hierarchy (People Tree and Organization Tree)
export { HrHierarchyService } from './services/hr-hierarchy.service';
export type {
  EmployeeRef,
  OrganizationalUnitRef,
  EmployeeTreeNode,
  EmployeeHierarchyResponse,
  EmployeeForestResponse,
  OrganizationalUnitTreeNode,
  OrganizationalUnitHierarchyResponse,
  HierarchyAncestorsResponse,
} from './models/hierarchy.model';

// Tree Components
export { PeopleTreeComponent } from './people-tree/people-tree.component';
export { OrganizationTreeComponent } from './organization-tree/organization-tree.component';
