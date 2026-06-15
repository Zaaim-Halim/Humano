// Manager persona — approvals + team oversight.
// Listing endpoints are keyed by approver/requestor employee id; the data layer
// is contract-complete and lights up once a current-employee seam resolves.

// Enums
export { ApprovalType } from './models/enums/approval-type.enum';
export { WorkflowStatus } from './models/enums/workflow-status.enum';
export { ApprovalDecision } from './models/enums/approval-decision.enum';

// Approvals
export { ApprovalService } from './services/approval.service';
export type { PendingApprovalSummary, ApprovalWorkflow, ApprovalHistoryItem, ApprovalDecisionRequest } from './models/approval.model';
