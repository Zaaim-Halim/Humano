import { EmployeeProcessStatus } from './enums/employee-process-status.enum';

/** One task on a leaver's checklist. `assignedTo*` is null for tasks owned by HR/IT. */
export interface OffboardingTask {
  taskId: string;
  title: string;
  description: string | null;
  dueDate: string | null;
  completed: boolean;
  completionDate: string | null;
  assignedToId: string | null;
  assignedToName: string | null;
  completionNotes: string | null;
}

/** Hand-over signals, read from the checklist. */
export interface OffboardingProgress {
  pendingRequestsProcessed: boolean;
  settlementCalculated: boolean;
  benefitsTerminated: boolean;
  knowledgeTransferCompleted: boolean;
  assetsReturned: boolean;
  exitInterviewConducted: boolean;
  accessRevoked: boolean;
  totalTasks: number;
  completedTasks: number;
}

/**
 * Final settlement. `null` parts aren't tracked yet (no leave-balance / overtime pay-rate model)
 * and are left out of `totalSettlement`.
 */
export interface FinalSettlement {
  remainingLeaveDays: number | null;
  leaveEncashment: number | null;
  pendingExpenseReimbursements: number;
  pendingOvertimePay: number | null;
  totalSettlement: number;
}

/** Full process — `GET /api/hr/workflows/offboarding/{processId}`, also returned by start and complete-task. */
export interface OffboardingProcess {
  processId: string;
  workflowId: string | null;
  employeeId: string;
  employeeName: string;
  employeeEmail: string | null;
  workflowStatus: string;
  processStatus: EmployeeProcessStatus;
  currentState: string | null;
  startDate: string;
  lastWorkingDate: string;
  /** Why they're leaving, as entered when the offboarding started. */
  offboardingReason: string | null;
  terminationReasonId: string | null;
  terminationReasonName: string | null;
  exitInterview: boolean;
  completionPercentage: number;
  tasks: OffboardingTask[];
  progress: OffboardingProgress;
  settlement: FinalSettlement;
  createdDate: string | null;
  lastModifiedDate: string | null;
}

/** Body for `POST /api/hr/workflows/offboarding`. */
export interface InitiateOffboardingRequest {
  employeeId: string;
  lastWorkingDate: string;
  reason: string;
  terminationReasonId?: string;
  conductExitInterview: boolean;
  exitInterviewerId?: string;
  notes?: string;
}
