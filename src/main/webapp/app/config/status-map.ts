// The one status -> color/label source. Components must resolve status colors
// through STATUS_MAP (never inline colors). Derived from the design handoff.

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'brand';

export interface StatusMeta {
  tone: BadgeTone;
  label: string;
  icon?: string;
}

export const STATUS_MAP: Record<string, StatusMeta> = {
  // approvals / requests (LeaveStatus, OvertimeApprovalStatus, ExpenseClaimStatus)
  APPROVED: { tone: 'success', label: 'Approved' },
  PENDING: { tone: 'warning', label: 'Pending' },
  REJECTED: { tone: 'danger', label: 'Rejected' },
  ON_HOLD: { tone: 'warning', label: 'On hold' },
  CANCELLED: { tone: 'neutral', label: 'Cancelled' },
  CANCELED: { tone: 'neutral', label: 'Canceled' }, // backend LeaveStatus spelling (single L)
  // approval workflows (WorkflowStatus) — APPROVED/REJECTED/ON_HOLD/CANCELLED/DRAFT shared above
  IN_PROGRESS: { tone: 'info', label: 'In progress' },
  PLANNED: { tone: 'info', label: 'Planned' },
  DELAYED: { tone: 'warning', label: 'Delayed' },
  PENDING_APPROVAL: { tone: 'warning', label: 'Pending approval' },
  COMPLETED: { tone: 'success', label: 'Completed' },
  ESCALATED: { tone: 'warning', label: 'Escalated' },
  // payroll runs (RunStatus: DRAFT → CALCULATED → APPROVED → POSTED)
  DRAFT: { tone: 'neutral', label: 'Draft' },
  CALCULATED: { tone: 'info', label: 'Calculated' },
  PROCESSING: { tone: 'info', label: 'Processing' },
  REVIEW: { tone: 'warning', label: 'In review' },
  POSTED: { tone: 'success', label: 'Posted' },
  PAID: { tone: 'success', label: 'Paid' },
  // invoices / payments
  DUE: { tone: 'warning', label: 'Due' },
  OVERDUE: { tone: 'danger', label: 'Overdue' },
  REFUNDED: { tone: 'info', label: 'Refunded' },
  FAILED: { tone: 'danger', label: 'Failed' },
  // people (EmployeeStatus)
  ACTIVE: { tone: 'success', label: 'Active' },
  INACTIVE: { tone: 'neutral', label: 'Inactive' },
  ONBOARDING: { tone: 'info', label: 'Onboarding' },
  OFFBOARDING: { tone: 'warning', label: 'Offboarding' },
  TERMINATED: { tone: 'neutral', label: 'Terminated' },
  ON_LEAVE: { tone: 'info', label: 'On leave' },
  // subscriptions (SubscriptionStatus)
  TRIAL: { tone: 'info', label: 'Trial' },
  PENDING_PAYMENT: { tone: 'warning', label: 'Pending payment' },
  PAST_DUE: { tone: 'danger', label: 'Past due' },
  EXPIRED: { tone: 'neutral', label: 'Expired' },
  SUSPENDED: { tone: 'danger', label: 'Suspended' },
  // tenants (TenantStatus)
  PENDING_SETUP: { tone: 'neutral', label: 'Pending setup' },
  PROVISIONING: { tone: 'info', label: 'Provisioning' },
  PROVISIONING_FAILED: { tone: 'danger', label: 'Provisioning failed' },
  MIGRATION_FAILED: { tone: 'danger', label: 'Migration failed' },
  DEACTIVATED: { tone: 'neutral', label: 'Deactivated' },
  DELETED: { tone: 'neutral', label: 'Deleted' },
  // review cycles (ReviewCyclePhase) — DRAFT/COMPLETED shared above
  SELF_ASSESSMENT: { tone: 'info', label: 'Self-assessment' },
  MANAGER_REVIEW: { tone: 'info', label: 'Manager review' },
  CALIBRATION: { tone: 'warning', label: 'Calibration' },
  FEEDBACK_DELIVERY: { tone: 'brand', label: 'Feedback' },
  GOAL_SETTING: { tone: 'brand', label: 'Goal setting' },
  ARCHIVED: { tone: 'neutral', label: 'Archived' },
  // performance reviews (PerformanceReviewStatus)
  NOT_STARTED: { tone: 'neutral', label: 'Not started' },
  SELF_SUBMITTED: { tone: 'info', label: 'Self-assessed' },
  MANAGER_SUBMITTED: { tone: 'warning', label: 'Reviewed' },
  SHARED: { tone: 'success', label: 'Shared' },
  EXCLUDED: { tone: 'neutral', label: 'Excluded' },
  // feedback requests (FeedbackRequestStatus) — PENDING_APPROVAL/CANCELLED shared above
  REQUESTED: { tone: 'info', label: 'Requested' },
  SUBMITTED: { tone: 'success', label: 'Submitted' },
  DECLINED: { tone: 'neutral', label: 'Declined' },
};
