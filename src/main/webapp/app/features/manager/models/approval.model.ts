import { ApprovalDecision } from './enums/approval-decision.enum';
import { ApprovalType } from './enums/approval-type.enum';
import { WorkflowStatus } from './enums/workflow-status.enum';

/**
 * Row projection — `GET /api/hr/workflows/approvals/pending/{approverId}`
 * (unpaged) and `/pending/{approverId}/paged` (`Page`). Instants are ISO-8601
 * strings on the wire.
 */
export interface PendingApprovalSummary {
  approvalRequestId: string;
  approvalType: ApprovalType;
  entityId: string;
  entityType: string | null;
  entityDescription: string | null;
  requestorId: string;
  requestorName: string | null;
  status: WorkflowStatus;
  priority: number | null;
  amount: number | null;
  daysCount: number | null;
  submittedAt: string | null;
  dueDate: string | null;
  isOverdue: boolean;
  daysWaiting: number;
}

/** One decided step in the multi-level approval chain. */
export interface ApprovalHistoryItem {
  level: number | null;
  approverId: string | null;
  approverName: string | null;
  decision: string | null;
  comments: string | null;
  decidedAt: string | null;
}

/**
 * Full workflow detail — `GET /api/hr/workflows/approvals/{approvalId}`. Carries
 * the multi-level chain via `currentLevel`/`totalLevels` + `approvalHistory`.
 */
export interface ApprovalWorkflow {
  approvalRequestId: string;
  workflowId: string | null;
  approvalType: ApprovalType;
  entityId: string;
  entityType: string | null;
  status: WorkflowStatus;
  requestorId: string;
  requestorName: string | null;
  currentApproverId: string | null;
  currentApproverName: string | null;
  currentLevel: number | null;
  totalLevels: number | null;
  amount: number | null;
  daysCount: number | null;
  priority: number | null;
  submittedAt: string | null;
  decidedAt: string | null;
  dueDate: string | null;
  approverComments: string | null;
  approvalHistory: ApprovalHistoryItem[] | null;
  createdDate: string | null;
  lastModifiedDate: string | null;
}

/** Body for `POST /api/hr/workflows/approvals/{approvalId}/decide`. */
export interface ApprovalDecisionRequest {
  decision: ApprovalDecision;
  comments?: string;
}
