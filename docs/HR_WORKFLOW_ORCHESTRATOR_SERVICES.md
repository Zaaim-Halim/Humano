# HR Workflow/Orchestrator Services - Design Document

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Analysis](#current-state-analysis)
3. [Identified Complex Operations](#identified-complex-operations)
4. [Proposed Workflow Services](#proposed-workflow-services)
5. [Architecture Design](#architecture-design)
6. [Detailed Service Specifications](#detailed-service-specifications)
7. [Event-Driven Architecture](#event-driven-architecture)
8. [Implementation Priorities](#implementation-priorities)
9. [Technical Considerations](#technical-considerations)

---

## Executive Summary

This document outlines the design and implementation plan for **Workflow/Orchestrator Services** in the HR module of the Humano application. These services will coordinate complex, multi-step business processes that span multiple entities and services, ensuring data consistency, proper sequencing, and comprehensive audit trails.

### Why Workflow Services?

Currently, the HR module has individual services handling CRUD operations for each entity (Employee, LeaveRequest, ExpenseClaim, etc.). However, many real-world HR operations involve:

- **Multiple entities** that must be updated atomically
- **Sequential steps** that depend on previous outcomes
- **Approval workflows** with multiple stakeholders
- **Notifications** to various parties
- **Audit requirements** for compliance
- **Rollback capabilities** when operations fail

---

## Current State Analysis

### Existing Services (17 Services)

| Service                     | Primary Responsibility                | Complexity |
| --------------------------- | ------------------------------------- | ---------- |
| `EmployeeProfileService`    | Employee CRUD, profile management     | Medium     |
| `LeaveRequestService`       | Leave requests, approval processing   | Medium     |
| `ExpenseClaimService`       | Expense claims, approval processing   | Medium     |
| `OvertimeRecordService`     | Overtime records, approval processing | Medium     |
| `AttendanceService`         | Attendance tracking, events           | Low-Medium |
| `TimesheetService`          | Time tracking per project             | Low        |
| `PerformanceReviewService`  | Performance evaluations               | Medium     |
| `TrainingService`           | Training programs, enrollments        | Medium     |
| `BenefitService`            | Employee benefits management          | Low        |
| `HealthInsuranceService`    | Health insurance records              | Low        |
| `DepartmentService`         | Department management                 | Low        |
| `PositionService`           | Position/role management              | Low        |
| `OrganizationalUnitService` | Org structure management              | Low        |
| `ProjectService`            | Project management                    | Low        |
| `SkillService`              | Skills management                     | Low        |
| `SurveyService`             | Employee surveys                      | Low        |
| `EmployeeDocumentService`   | Document management                   | Low        |

### Existing Domain Support for Workflows

The system already has foundational entities for workflow management:

- **`EmployeeProcess`** - Tracks onboarding/offboarding processes
- **`EmployeeProcessTask`** - Individual tasks within a process
- **`EmployeeProcessType`** - ONBOARDING, OFFBOARDING
- **`EmployeeProcessStatus`** - PLANNED, IN_PROGRESS, COMPLETED, DELAYED, CANCELLED

### Current Gaps

1. **No orchestration layer** - Services operate independently
2. **No workflow state management** - Process states not tracked holistically
3. **Limited event publishing** - Only tenant-related events exist
4. **No compensation/rollback logic** - Failed multi-step operations leave inconsistent state
5. **No notification integration** - Stakeholders not automatically notified
6. **No SLA/deadline tracking** - Time-sensitive workflows not monitored

---

## Identified Complex Operations

Based on careful analysis of the HR domain, the following complex operations require orchestration:

### 1. Employee Onboarding Workflow

**Complexity: HIGH** | **Priority: CRITICAL**

A new employee joining the organization requires coordination across multiple systems:

```
Trigger: HR creates employee profile
    │
    ├── 1. Create Employee Profile
    │       └── Validate user exists, create employee record
    │
    ├── 2. Assign to Department & Position
    │       └── Update organizational relationships
    │
    ├── 3. Setup Benefits & Insurance
    │       └── Create benefit enrollments, health insurance
    │
    ├── 4. Create Onboarding Process
    │       └── Generate task checklist from template
    │
    ├── 5. Assign Required Trainings
    │       └── Enroll in mandatory training programs
    │
    ├── 6. Setup Initial Attendance Profile
    │       └── Configure attendance rules
    │
    ├── 7. Notify Stakeholders
    │       └── Manager, IT, HR, Payroll notifications
    │
    └── 8. Generate Welcome Package
            └── Documents, credentials, orientation schedule
```

**Entities Involved:** Employee, Department, Position, Benefit, HealthInsurance, EmployeeProcess, EmployeeProcessTask, EmployeeTraining, Training, EmployeeNotification, EmployeeDocument

---

### 2. Employee Offboarding Workflow

**Complexity: HIGH** | **Priority: CRITICAL**

Employee departure requires careful coordination to ensure compliance and asset recovery:

```
Trigger: HR initiates offboarding OR employee resignation
    │
    ├── 1. Create Offboarding Process
    │       └── Set end date, reason, generate task checklist
    │
    ├── 2. Process Pending Requests
    │       └── Cancel/process pending leave, expense, overtime requests
    │
    ├── 3. Calculate Final Settlements
    │       └── Remaining leave balance, pending reimbursements
    │
    ├── 4. Terminate Benefits
    │       └── Schedule benefit termination dates
    │
    ├── 5. Knowledge Transfer Tasks
    │       └── Create handover documentation tasks
    │
    ├── 6. Asset Recovery Tasks
    │       └── Equipment return, access revocation checklist
    │
    ├── 7. Conduct Exit Interview
    │       └── Schedule and track exit survey
    │
    ├── 8. Update Employee Status
    │       └── Mark as TERMINATED/RESIGNED on end date
    │
    └── 9. Archive & Compliance
            └── Archive documents, ensure data retention compliance
```

**Entities Involved:** Employee, EmployeeProcess, EmployeeProcessTask, LeaveRequest, ExpenseClaim, OvertimeRecord, Benefit, HealthInsurance, Survey, EmployeeDocument

---

### 3. Leave Request Workflow

**Complexity: MEDIUM** | **Priority: HIGH**

Leave requests involve validation, approval chains, and balance management:

```
Trigger: Employee submits leave request
    │
    ├── 1. Validate Request
    │       ├── Check date conflicts
    │       ├── Verify leave balance
    │       └── Check blackout periods
    │
    ├── 2. Determine Approval Chain
    │       ├── Direct manager
    │       ├── Department head (if > X days)
    │       └── HR (for special leave types)
    │
    ├── 3. Notify Approvers
    │       └── Send approval request notifications
    │
    ├── 4. Process Approval/Rejection
    │       ├── Update request status
    │       ├── Deduct leave balance (if approved)
    │       └── Notify employee of decision
    │
    ├── 5. Update Team Calendar
    │       └── Block dates, notify team members
    │
    └── 6. Handle Cancellation (if applicable)
            └── Restore balance, update calendar
```

**Entities Involved:** LeaveRequest, Employee, EmployeeNotification, (LeaveBalance - to be created)

---

### 4. Expense Claim Workflow

**Complexity: MEDIUM** | **Priority: HIGH**

Expense reimbursements require validation, multi-level approval, and payment integration:

```
Trigger: Employee submits expense claim
    │
    ├── 1. Validate Claim
    │       ├── Check policy compliance
    │       ├── Verify receipt attachments
    │       └── Validate expense categories
    │
    ├── 2. Determine Approval Chain
    │       ├── Manager approval (< threshold)
    │       ├── Finance approval (> threshold)
    │       └── Executive approval (> high threshold)
    │
    ├── 3. Process Each Approval Level
    │       ├── Notify current approver
    │       ├── Wait for decision
    │       └── Escalate if overdue
    │
    ├── 4. Finance Processing
    │       ├── Verify budget availability
    │       └── Queue for payment
    │
    ├── 5. Payment Execution
    │       └── Integrate with payroll/payment system
    │
    └── 6. Notification & Audit
            └── Notify employee, create audit trail
```

**Entities Involved:** ExpenseClaim, Employee, EmployeeNotification, (ExpensePolicy, PaymentQueue - to be created)

---

### 5. Performance Review Cycle Workflow

**Complexity: HIGH** | **Priority: MEDIUM**

Annual/quarterly performance reviews involve multiple participants and deadlines:

```
Trigger: HR initiates review cycle OR scheduled trigger
    │
    ├── 1. Initialize Review Cycle
    │       ├── Define review period
    │       ├── Set deadlines for each phase
    │       └── Select participants
    │
    ├── 2. Self-Assessment Phase
    │       ├── Notify employees
    │       ├── Track completion
    │       └── Send reminders
    │
    ├── 3. Manager Review Phase
    │       ├── Notify managers
    │       ├── Provide employee self-assessments
    │       └── Track completion
    │
    ├── 4. Calibration Phase (Optional)
    │       ├── Aggregate department ratings
    │       └── Facilitate calibration meetings
    │
    ├── 5. Feedback Delivery
    │       ├── Schedule 1:1 meetings
    │       └── Track meeting completion
    │
    ├── 6. Goal Setting
    │       ├── Create goals for next period
    │       └── Link to development plans
    │
    └── 7. Cycle Completion
            └── Generate reports, archive data
```

**Entities Involved:** PerformanceReview, Employee, EmployeeNotification, (ReviewCycle, Goal, DevelopmentPlan - to be created)

---

### 6. Training Enrollment & Completion Workflow

**Complexity: MEDIUM** | **Priority: MEDIUM**

Training management involves enrollment, tracking, and certification:

```
Trigger: Employee/Manager requests training OR mandatory assignment
    │
    ├── 1. Enrollment Processing
    │       ├── Check prerequisites
    │       ├── Verify seat availability
    │       └── Manager approval (if required)
    │
    ├── 2. Pre-Training Setup
    │       ├── Send enrollment confirmation
    │       ├── Provide pre-work materials
    │       └── Calendar integration
    │
    ├── 3. Training Delivery Tracking
    │       ├── Track attendance
    │       ├── Monitor progress (for online)
    │       └── Send reminders
    │
    ├── 4. Completion Processing
    │       ├── Record completion status
    │       ├── Update employee skills
    │       └── Issue certificates
    │
    └── 5. Follow-up
            ├── Collect feedback
            └── Schedule refresher (if required)
```

**Entities Involved:** Training, EmployeeTraining, Employee, Skill, EmployeeSkill, EmployeeNotification, EmployeeDocument

---

### 7. Overtime Approval Workflow

**Complexity: LOW-MEDIUM** | **Priority: MEDIUM**

Overtime requests require pre-approval and post-verification:

```
Trigger: Employee submits overtime request
    │
    ├── 1. Pre-Approval (Optional)
    │       ├── Validate business justification
    │       └── Manager approval
    │
    ├── 2. Overtime Execution
    │       └── Employee works overtime
    │
    ├── 3. Post-Verification
    │       ├── Verify actual hours via attendance
    │       └── Manager confirmation
    │
    ├── 4. Payroll Integration
    │       └── Calculate overtime pay, send to payroll
    │
    └── 5. Reporting
            └── Update overtime reports, budget tracking
```

**Entities Involved:** OvertimeRecord, Employee, Attendance, EmployeeNotification

---

### 8. Position/Department Transfer Workflow

**Complexity: MEDIUM** | **Priority: MEDIUM**

Internal transfers require coordination between departments:

```
Trigger: Transfer request initiated
    │
    ├── 1. Request Validation
    │       ├── Check position availability
    │       └── Verify employee eligibility
    │
    ├── 2. Approval Chain
    │       ├── Current manager approval
    │       ├── New manager approval
    │       └── HR approval
    │
    ├── 3. Transition Planning
    │       ├── Set effective date
    │       ├── Create handover tasks
    │       └── Knowledge transfer plan
    │
    ├── 4. Execute Transfer
    │       ├── Update employee department/position
    │       ├── Update reporting relationships
    │       └── Record position history
    │
    ├── 5. Post-Transfer Setup
    │       ├── Update access permissions
    │       ├── Assign new trainings
    │       └── Update benefits (if applicable)
    │
    └── 6. Notifications
            └── Notify all stakeholders
```

**Entities Involved:** Employee, Department, Position, OrganizationalUnit, EmployeePositionHistory, EmployeeNotification

---

### 9. Timesheet Submission & Approval Workflow

**Complexity: LOW-MEDIUM** | **Priority: LOW**

Weekly/bi-weekly timesheet submissions:

```
Trigger: End of pay period OR manual submission
    │
    ├── 1. Submission Reminder
    │       └── Notify employees of pending timesheets
    │
    ├── 2. Validation
    │       ├── Check minimum hours
    │       ├── Validate project allocations
    │       └── Cross-check with attendance
    │
    ├── 3. Manager Approval
    │       ├── Notify manager
    │       └── Bulk approval support
    │
    ├── 4. Payroll Integration
    │       └── Send approved hours to payroll
    │
    └── 5. Reporting
            └── Generate utilization reports
```

**Entities Involved:** Timesheet, Employee, Project, Attendance, EmployeeNotification

---

### 10. Bulk Operations Workflow

**Complexity: MEDIUM** | **Priority: LOW**

HR often needs to perform bulk operations:

```
Operations:
    ├── Bulk Salary Adjustments
    ├── Mass Training Enrollments
    ├── Department Restructuring
    ├── Annual Leave Balance Reset
    └── Bulk Performance Review Initiation

Common Pattern:
    │
    ├── 1. Upload/Select Records
    ├── 2. Validation & Preview
    ├── 3. Approval (if required)
    ├── 4. Batch Processing with Progress
    ├── 5. Error Handling & Retry
    └── 6. Completion Report
```

---

## Proposed Workflow Services

Based on the analysis above, the following orchestrator services are recommended:

### Service Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        WORKFLOW ORCHESTRATION LAYER                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────┐ │
│  │  EmployeeLifecycle   │  │  ApprovalWorkflow    │  │  BulkOperation │ │
│  │  WorkflowService     │  │  OrchestratorService │  │  Service       │ │
│  └──────────────────────┘  └──────────────────────┘  └────────────────┘ │
│                                                                          │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────┐ │
│  │  PerformanceReview   │  │  TrainingWorkflow    │  │  Transfer      │ │
│  │  CycleService        │  │  Service             │  │  WorkflowSvc   │ │
│  └──────────────────────┘  └──────────────────────┘  └────────────────┘ │
│                                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                         WORKFLOW INFRASTRUCTURE                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────┐ │
│  │  WorkflowState       │  │  NotificationOrch    │  │  Workflow      │ │
│  │  Manager             │  │  estrationService    │  │  AuditService  │ │
│  └──────────────────────┘  └──────────────────────┘  └────────────────┘ │
│                                                                          │
│  ┌──────────────────────┐  ┌──────────────────────┐                     │
│  │  DeadlineMonitor     │  │  CompensationSvc     │                     │
│  │  Service             │  │  (Rollback Handler)  │                     │
│  └──────────────────────┘  └──────────────────────┘                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         EXISTING SERVICE LAYER                           │
├─────────────────────────────────────────────────────────────────────────┤
│  EmployeeProfileService │ LeaveRequestService │ ExpenseClaimService     │
│  OvertimeRecordService  │ AttendanceService   │ TimesheetService        │
│  PerformanceReviewSvc   │ TrainingService     │ BenefitService          │
│  ... (all 17 existing services)                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Service Specifications

### 1. EmployeeLifecycleWorkflowService

**Purpose:** Orchestrates complete employee onboarding and offboarding processes.

**Location:** `com.humano.service.hr.workflow.EmployeeLifecycleWorkflowService`

**Dependencies:**

- EmployeeProfileService
- DepartmentService
- PositionService
- BenefitService
- HealthInsuranceService
- TrainingService
- NotificationOrchestrationService
- WorkflowStateManager

**Key Methods:**

```java
public interface EmployeeLifecycleWorkflowService {
  // Onboarding
  OnboardingProcessResponse initiateOnboarding(InitiateOnboardingRequest request);
  OnboardingProcessResponse getOnboardingStatus(UUID processId);
  void completeOnboardingTask(UUID processId, UUID taskId, CompleteTaskRequest request);
  void cancelOnboarding(UUID processId, String reason);

  // Offboarding
  OffboardingProcessResponse initiateOffboarding(InitiateOffboardingRequest request);
  OffboardingProcessResponse getOffboardingStatus(UUID processId);
  void completeOffboardingTask(UUID processId, UUID taskId, CompleteTaskRequest request);
  FinalSettlementResponse calculateFinalSettlement(UUID employeeId);

  // Process Management
  Page<EmployeeProcessResponse> getActiveProcesses(ProcessSearchCriteria criteria, Pageable pageable);
  void escalateOverdueTasks(UUID processId);
  ProcessAnalyticsResponse getProcessAnalytics(DateRange dateRange);
}

```

**Workflow States:**

```
ONBOARDING:
  INITIATED → PROFILE_CREATED → DEPARTMENT_ASSIGNED → BENEFITS_SETUP →
  TRAININGS_ASSIGNED → TASKS_IN_PROGRESS → COMPLETED

OFFBOARDING:
  INITIATED → PENDING_REQUESTS_PROCESSED → SETTLEMENTS_CALCULATED →
  BENEFITS_TERMINATED → TASKS_IN_PROGRESS → EXIT_INTERVIEW_DONE →
  COMPLETED → ARCHIVED
```

---

### 2. ApprovalWorkflowOrchestratorService

**Purpose:** Generic approval workflow engine for leave requests, expense claims, overtime, etc.

**Location:** `com.humano.service.hr.workflow.ApprovalWorkflowOrchestratorService`

**Dependencies:**

- LeaveRequestService
- ExpenseClaimService
- OvertimeRecordService
- NotificationOrchestrationService
- WorkflowStateManager

**Key Methods:**

```java
public interface ApprovalWorkflowOrchestratorService {
  // Generic Approval Processing
  <T extends ApprovableEntity> ApprovalWorkflowResponse submitForApproval(ApprovalRequest<T> request);

  ApprovalWorkflowResponse processApprovalDecision(UUID workflowId, ApprovalDecision decision);

  void escalateToNextApprover(UUID workflowId);
  void withdrawApprovalRequest(UUID workflowId, String reason);

  // Approval Chain Management
  List<ApproverInfo> getApprovalChain(ApprovalType type, ApprovalContext context);
  ApprovalWorkflowResponse getCurrentApprovalStatus(UUID workflowId);

  // Leave-Specific
  LeaveApprovalResponse submitLeaveRequest(SubmitLeaveRequest request);
  LeaveApprovalResponse processLeaveApproval(UUID requestId, LeaveApprovalDecision decision);
  void cancelLeaveRequest(UUID requestId, String reason);

  // Expense-Specific
  ExpenseApprovalResponse submitExpenseClaim(SubmitExpenseClaimRequest request);
  ExpenseApprovalResponse processExpenseApproval(UUID claimId, ExpenseApprovalDecision decision);

  // Overtime-Specific
  OvertimeApprovalResponse submitOvertimeRequest(SubmitOvertimeRequest request);
  OvertimeApprovalResponse processOvertimeApproval(UUID recordId, OvertimeApprovalDecision decision);

  // Bulk Approvals
  BulkApprovalResponse processBulkApproval(BulkApprovalRequest request);

  // Analytics
  ApprovalAnalyticsResponse getApprovalAnalytics(ApprovalType type, DateRange range);
  List<PendingApprovalSummary> getPendingApprovalsForApprover(UUID approverId);
}

```

**Approval Types:**

```java
public enum ApprovalType {
  LEAVE_REQUEST,
  EXPENSE_CLAIM,
  OVERTIME_REQUEST,
  TRAINING_REQUEST,
  POSITION_TRANSFER,
  SALARY_ADJUSTMENT,
  EQUIPMENT_REQUEST,
}

```

---

### 3. PerformanceReviewCycleService

**Purpose:** Manages end-to-end performance review cycles.

**Location:** `com.humano.service.hr.workflow.PerformanceReviewCycleService`

**Dependencies:**

- PerformanceReviewService
- EmployeeProfileService
- NotificationOrchestrationService
- WorkflowStateManager

**Key Methods:**

```java
public interface PerformanceReviewCycleService {
  // Cycle Management
  ReviewCycleResponse initiateCycle(InitiateReviewCycleRequest request);
  ReviewCycleResponse getCycleStatus(UUID cycleId);
  void closeCycle(UUID cycleId);

  // Phase Management
  void startSelfAssessmentPhase(UUID cycleId);
  void startManagerReviewPhase(UUID cycleId);
  void startCalibrationPhase(UUID cycleId);
  void startFeedbackDeliveryPhase(UUID cycleId);

  // Individual Review Management
  void submitSelfAssessment(UUID cycleId, UUID employeeId, SelfAssessmentRequest request);
  void submitManagerReview(UUID cycleId, UUID employeeId, ManagerReviewRequest request);
  void recordFeedbackMeeting(UUID cycleId, UUID employeeId, FeedbackMeetingRecord record);

  // Tracking & Reminders
  CycleProgressResponse getCycleProgress(UUID cycleId);
  void sendPhaseReminders(UUID cycleId);
  List<OverdueReviewInfo> getOverdueReviews(UUID cycleId);

  // Reporting
  CycleReportResponse generateCycleReport(UUID cycleId);
  DepartmentRatingDistribution getRatingDistribution(UUID cycleId, UUID departmentId);
}

```

**Review Cycle Phases:**

```
DRAFT → SELF_ASSESSMENT → MANAGER_REVIEW → CALIBRATION →
FEEDBACK_DELIVERY → GOAL_SETTING → COMPLETED → ARCHIVED
```

---

### 4. TrainingWorkflowService

**Purpose:** Manages training enrollment, completion tracking, and certification workflows.

**Location:** `com.humano.service.hr.workflow.TrainingWorkflowService`

**Dependencies:**

- TrainingService
- SkillService
- NotificationOrchestrationService
- WorkflowStateManager

**Key Methods:**

```java
public interface TrainingWorkflowService {
  // Enrollment Workflow
  EnrollmentResponse requestEnrollment(TrainingEnrollmentRequest request);
  EnrollmentResponse processEnrollmentApproval(UUID enrollmentId, ApprovalDecision decision);
  void cancelEnrollment(UUID enrollmentId, String reason);

  // Mandatory Training Assignment
  BulkEnrollmentResponse assignMandatoryTraining(MandatoryTrainingAssignmentRequest request);
  ComplianceReportResponse getMandatoryTrainingCompliance(UUID departmentId);

  // Training Delivery
  void recordAttendance(UUID enrollmentId, AttendanceRecord record);
  void updateProgress(UUID enrollmentId, ProgressUpdate update);

  // Completion Processing
  CompletionResponse recordCompletion(UUID enrollmentId, CompletionRequest request);
  void issueCertificate(UUID enrollmentId);
  void updateEmployeeSkills(UUID enrollmentId);

  // Feedback & Follow-up
  void collectFeedback(UUID enrollmentId, TrainingFeedback feedback);
  void scheduleRefresherTraining(UUID employeeId, UUID trainingId, LocalDate date);

  // Analytics
  TrainingAnalyticsResponse getTrainingAnalytics(DateRange range);
  EmployeeTrainingHistoryResponse getEmployeeTrainingHistory(UUID employeeId);
}

```

---

### 5. TransferWorkflowService

**Purpose:** Manages internal position and department transfers.

**Location:** `com.humano.service.hr.workflow.TransferWorkflowService`

**Dependencies:**

- EmployeeProfileService
- DepartmentService
- PositionService
- OrganizationalUnitService
- NotificationOrchestrationService
- WorkflowStateManager

**Key Methods:**

```java
public interface TransferWorkflowService {
  // Transfer Initiation
  TransferRequestResponse initiateTransfer(InitiateTransferRequest request);
  TransferRequestResponse getTransferStatus(UUID transferId);

  // Approval Processing
  void processCurrentManagerApproval(UUID transferId, ApprovalDecision decision);
  void processNewManagerApproval(UUID transferId, ApprovalDecision decision);
  void processHRApproval(UUID transferId, ApprovalDecision decision);

  // Transfer Execution
  void executeTransfer(UUID transferId);
  void scheduleTransfer(UUID transferId, LocalDate effectiveDate);
  void cancelTransfer(UUID transferId, String reason);

  // Handover Management
  HandoverPlanResponse createHandoverPlan(UUID transferId, HandoverPlanRequest request);
  void completeHandoverTask(UUID transferId, UUID taskId);

  // History & Reporting
  Page<PositionHistoryResponse> getEmployeePositionHistory(UUID employeeId, Pageable pageable);
  TransferAnalyticsResponse getTransferAnalytics(DateRange range);
}

```

---

### 6. WorkflowStateManager (Infrastructure)

**Purpose:** Centralized workflow state persistence and management.

**Location:** `com.humano.service.hr.workflow.infrastructure.WorkflowStateManager`

**Key Methods:**

```java
public interface WorkflowStateManager {
  // State Management
  WorkflowInstance createWorkflow(WorkflowType type, UUID entityId, Map<String, Object> context);
  WorkflowInstance getWorkflow(UUID workflowId);
  void transitionState(UUID workflowId, String targetState, String reason);
  void completeWorkflow(UUID workflowId, WorkflowOutcome outcome);
  void failWorkflow(UUID workflowId, String errorMessage, Exception cause);

  // State Queries
  List<WorkflowInstance> getWorkflowsByState(WorkflowType type, String state);
  List<WorkflowInstance> getWorkflowsByEntity(UUID entityId);
  boolean isWorkflowActive(UUID workflowId);

  // History & Audit
  List<StateTransition> getWorkflowHistory(UUID workflowId);
  WorkflowAuditLog getAuditLog(UUID workflowId);
}

```

---

### 7. NotificationOrchestrationService (Infrastructure)

**Purpose:** Coordinates notifications across all workflows.

**Location:** `com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService`

**Key Methods:**

```java
public interface NotificationOrchestrationService {
  // Notification Dispatch
  void notifyApprovalRequired(ApprovalNotificationContext context);
  void notifyApprovalDecision(ApprovalDecisionNotificationContext context);
  void notifyTaskAssignment(TaskAssignmentContext context);
  void notifyDeadlineApproaching(DeadlineNotificationContext context);
  void notifyWorkflowCompletion(WorkflowCompletionContext context);

  // Bulk Notifications
  void sendBulkNotification(BulkNotificationRequest request);

  // Reminder Management
  void scheduleReminder(ReminderScheduleRequest request);
  void cancelReminder(UUID reminderId);

  // Notification Preferences
  NotificationPreferences getEmployeePreferences(UUID employeeId);
  void updateEmployeePreferences(UUID employeeId, NotificationPreferences preferences);

  // Channels
  void sendEmail(EmailNotification notification);
  void sendInAppNotification(InAppNotification notification);
  void sendPushNotification(PushNotification notification);
}

```

---

### 8. DeadlineMonitorService (Infrastructure)

**Purpose:** Monitors workflow deadlines and triggers escalations.

**Location:** `com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService`

**Key Methods:**

```java
public interface DeadlineMonitorService {
  // Deadline Registration
  void registerDeadline(DeadlineRegistration registration);
  void updateDeadline(UUID deadlineId, LocalDateTime newDeadline);
  void cancelDeadline(UUID deadlineId);

  // Monitoring
  @Scheduled(fixedRate = 3600000) // Hourly
  void checkApproachingDeadlines();

  @Scheduled(fixedRate = 3600000)
  void checkOverdueItems();

  // Escalation
  void escalate(UUID deadlineId, EscalationLevel level);
  void notifyStakeholders(UUID deadlineId, NotificationType type);

  // Reporting
  List<DeadlineStatus> getDeadlinesByWorkflow(UUID workflowId);
  OverdueItemsReport getOverdueItemsReport(DateRange range);
}

```

---

### 9. BulkOperationService

**Purpose:** Handles bulk HR operations with progress tracking and error handling.

**Location:** `com.humano.service.hr.workflow.BulkOperationService`

**Key Methods:**

```java
public interface BulkOperationService {
  // Bulk Operations
  BulkOperationResponse initiateBulkOperation(BulkOperationRequest request);
  BulkOperationStatus getOperationStatus(UUID operationId);
  void cancelOperation(UUID operationId);

  // Specific Bulk Operations
  BulkOperationResponse bulkEnrollTraining(BulkTrainingEnrollmentRequest request);
  BulkOperationResponse bulkUpdateSalaries(BulkSalaryUpdateRequest request);
  BulkOperationResponse bulkResetLeaveBalances(BulkLeaveResetRequest request);
  BulkOperationResponse bulkInitiateReviews(BulkReviewInitiationRequest request);

  // Progress & Error Handling
  OperationProgressResponse getProgress(UUID operationId);
  List<OperationError> getErrors(UUID operationId);
  void retryFailedItems(UUID operationId);

  // Reporting
  BulkOperationReport generateReport(UUID operationId);
}

```

---

## Event-Driven Architecture

### Proposed HR Events

To enable loose coupling and extensibility, the following events should be published:

```java
// Employee Lifecycle Events
EmployeeOnboardingInitiatedEvent
EmployeeOnboardingCompletedEvent
EmployeeOffboardingInitiatedEvent
EmployeeOffboardingCompletedEvent
EmployeeStatusChangedEvent
EmployeeTransferredEvent

// Approval Events
ApprovalRequestedEvent
ApprovalDecisionMadeEvent
ApprovalEscalatedEvent
ApprovalWithdrawnEvent

// Leave Events
LeaveRequestSubmittedEvent
LeaveRequestApprovedEvent
LeaveRequestRejectedEvent
LeaveRequestCancelledEvent

// Expense Events
ExpenseClaimSubmittedEvent
ExpenseClaimApprovedEvent
ExpenseClaimRejectedEvent
ExpenseClaimPaidEvent

// Training Events
TrainingEnrollmentRequestedEvent
TrainingEnrollmentApprovedEvent
TrainingCompletedEvent
CertificateIssuedEvent

// Performance Events
ReviewCycleInitiatedEvent
ReviewCyclePhaseChangedEvent
ReviewCycleCompletedEvent
PerformanceReviewSubmittedEvent

// Deadline Events
DeadlineApproachingEvent
DeadlineExceededEvent
EscalationTriggeredEvent
```

### Event Listeners Structure

```
com.humano.events.hr/
├── EmployeeLifecycleEventPublisher.java
├── ApprovalEventPublisher.java
├── TrainingEventPublisher.java
├── PerformanceEventPublisher.java
└── listeners/
    ├── NotificationEventListener.java
    ├── AuditEventListener.java
    ├── IntegrationEventListener.java
    └── AnalyticsEventListener.java
```

---

## Implementation Priorities

### Phase 1: Foundation (Weeks 1-2)

**Priority: CRITICAL**

1. **WorkflowStateManager** - Core state management infrastructure
2. **NotificationOrchestrationService** - Notification infrastructure
3. **Database schema** for workflow tracking
4. **Base event publishing** infrastructure

### Phase 2: Core Workflows (Weeks 3-5)

**Priority: HIGH**

1. **EmployeeLifecycleWorkflowService** - Onboarding/Offboarding
2. **ApprovalWorkflowOrchestratorService** - Leave, Expense, Overtime approvals

### Phase 3: Extended Workflows (Weeks 6-8)

**Priority: MEDIUM**

1. **PerformanceReviewCycleService** - Review cycle management
2. **TrainingWorkflowService** - Training enrollment and completion
3. **TransferWorkflowService** - Position/Department transfers

### Phase 4: Advanced Features (Weeks 9-10)

**Priority: LOW**

1. **DeadlineMonitorService** - SLA monitoring and escalations
2. **BulkOperationService** - Bulk operations support
3. **Analytics and reporting** enhancements

---

## Technical Considerations

### Database Schema Additions

```sql
-- Workflow Instance Tracking
CREATE TABLE workflow_instance (
    id UUID PRIMARY KEY,
    workflow_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    current_state VARCHAR(50) NOT NULL,
    context JSONB,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    outcome VARCHAR(20),
    created_by VARCHAR(50),
    created_date TIMESTAMP,
    last_modified_by VARCHAR(50),
    last_modified_date TIMESTAMP
);

-- State Transition History
CREATE TABLE workflow_state_transition (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow_instance(id),
    from_state VARCHAR(50),
    to_state VARCHAR(50) NOT NULL,
    reason VARCHAR(500),
    transitioned_by VARCHAR(50),
    transitioned_at TIMESTAMP NOT NULL
);

-- Approval Chain Configuration
CREATE TABLE approval_chain_config (
    id UUID PRIMARY KEY,
    approval_type VARCHAR(50) NOT NULL,
    sequence_order INT NOT NULL,
    approver_type VARCHAR(50) NOT NULL, -- DIRECT_MANAGER, DEPARTMENT_HEAD, HR, FINANCE
    condition_expression VARCHAR(500), -- SpEL expression for conditional approval
    is_active BOOLEAN DEFAULT TRUE
);

-- Deadline Tracking
CREATE TABLE workflow_deadline (
    id UUID PRIMARY KEY,
    workflow_id UUID REFERENCES workflow_instance(id),
    deadline_type VARCHAR(50) NOT NULL,
    deadline_at TIMESTAMP NOT NULL,
    warning_at TIMESTAMP,
    escalation_level INT DEFAULT 0,
    is_completed BOOLEAN DEFAULT FALSE
);
```

### Transaction Management

- Use **Saga pattern** for long-running workflows
- Implement **compensation handlers** for rollback scenarios
- Consider **eventual consistency** for cross-service operations

### Performance Considerations

- **Async processing** for non-critical workflow steps
- **Batch processing** for bulk operations
- **Caching** for frequently accessed workflow configurations
- **Database indexing** on workflow_instance(entity_id, workflow_type, current_state)

### Monitoring & Observability

- **Metrics:** Workflow duration, approval times, error rates
- **Logging:** Structured logging for all state transitions
- **Tracing:** Distributed tracing for cross-service workflows
- **Dashboards:** Real-time workflow status monitoring

---

## Appendix: File Structure

```
src/main/java/com/humano/
├── service/hr/workflow/
│   ├── EmployeeLifecycleWorkflowService.java
│   ├── ApprovalWorkflowOrchestratorService.java
│   ├── PerformanceReviewCycleService.java
│   ├── TrainingWorkflowService.java
│   ├── TransferWorkflowService.java
│   ├── BulkOperationService.java
│   ├── infrastructure/
│   │   ├── WorkflowStateManager.java
│   │   ├── NotificationOrchestrationService.java
│   │   ├── DeadlineMonitorService.java
│   │   └── CompensationService.java
│   └── impl/
│       ├── EmployeeLifecycleWorkflowServiceImpl.java
│       ├── ApprovalWorkflowOrchestratorServiceImpl.java
│       └── ... (implementations)
│
├── dto/hr/workflow/
│   ├── requests/
│   │   ├── InitiateOnboardingRequest.java
│   │   ├── InitiateOffboardingRequest.java
│   │   ├── SubmitLeaveRequest.java
│   │   └── ... (request DTOs)
│   └── responses/
│       ├── OnboardingProcessResponse.java
│       ├── ApprovalWorkflowResponse.java
│       └── ... (response DTOs)
│
├── domain/hr/workflow/
│   ├── WorkflowInstance.java
│   ├── StateTransition.java
│   ├── ApprovalChainConfig.java
│   └── WorkflowDeadline.java
│
├── repository/hr/workflow/
│   ├── WorkflowInstanceRepository.java
│   ├── StateTransitionRepository.java
│   └── ApprovalChainConfigRepository.java
│
├── events/hr/
│   ├── EmployeeLifecycleEventPublisher.java
│   ├── ApprovalEventPublisher.java
│   └── listeners/
│       └── ... (event listeners)
│
└── web/rest/hr/workflow/
    ├── OnboardingResource.java
    ├── OffboardingResource.java
    ├── ApprovalWorkflowResource.java
    └── ... (REST controllers)
```

---

## Conclusion

This document provides a comprehensive blueprint for implementing workflow/orchestrator services in the HR module. The proposed architecture:

1. **Maintains backward compatibility** with existing services
2. **Introduces orchestration layer** without modifying core services
3. **Enables complex multi-step operations** with proper state management
4. **Supports audit and compliance** requirements
5. **Provides extensibility** through event-driven architecture

The phased implementation approach ensures that critical workflows (onboarding, approvals) are delivered first while allowing for iterative enhancement of the system.

---

_Document Version: 1.0_
_Created: February 2026_
_Author: Development Team_
_Status: Design Complete - Ready for Implementation_
