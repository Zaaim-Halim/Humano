// The one status -> color/label source. Components must resolve status colors
// through STATUS_MAP (never inline colors). Derived from the design handoff.

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'brand';

export interface StatusMeta {
  tone: BadgeTone;
  label: string;
  icon?: string;
}

export const STATUS_MAP: Record<string, StatusMeta> = {
  // approvals / requests
  APPROVED: { tone: 'success', label: 'Approved' },
  PENDING: { tone: 'warning', label: 'Pending' },
  REJECTED: { tone: 'danger', label: 'Rejected' },
  ON_HOLD: { tone: 'warning', label: 'On hold' },
  CANCELLED: { tone: 'neutral', label: 'Cancelled' },
  // payroll runs
  DRAFT: { tone: 'neutral', label: 'Draft' },
  PROCESSING: { tone: 'info', label: 'Processing' },
  REVIEW: { tone: 'warning', label: 'In review' },
  PAID: { tone: 'success', label: 'Paid' },
  // invoices / payments
  DUE: { tone: 'warning', label: 'Due' },
  OVERDUE: { tone: 'danger', label: 'Overdue' },
  REFUNDED: { tone: 'info', label: 'Refunded' },
  FAILED: { tone: 'danger', label: 'Failed' },
  // people / subscriptions
  ACTIVE: { tone: 'success', label: 'Active' },
  INACTIVE: { tone: 'neutral', label: 'Inactive' },
  ONBOARDING: { tone: 'info', label: 'Onboarding' },
  OFFBOARDING: { tone: 'warning', label: 'Offboarding' },
  TERMINATED: { tone: 'neutral', label: 'Terminated' },
  ON_LEAVE: { tone: 'info', label: 'On leave' },
  TRIAL: { tone: 'info', label: 'Trial' },
  SUSPENDED: { tone: 'danger', label: 'Suspended' },
};
