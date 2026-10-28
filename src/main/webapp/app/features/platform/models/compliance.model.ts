/** `WAITING` before the warning period; `WARNING_DUE` once the email should go out; `WARNED` once it has. */
export type DeletionState = 'WAITING' | 'WARNING_DUE' | 'WARNED';

/** A workspace under the retention period: its subscription ended, and it is deleted on `deleteOn`. */
export interface ScheduledDeletion {
  tenantId: string;
  name: string;
  subdomain: string;
  tenantStatus: string;
  subscriptionStatus: string;
  endedAt: string;
  warnFrom: string;
  warningSentAt: string | null;
  /** Final once warned; before that, the earliest date (later if the warning goes out late). */
  deleteOn: string;
  state: DeletionState;
  /** Due for more than a day and still not sent — usually mail that is not configured. */
  warningOverdue: boolean;
}

/** `enabled` false: the nightly job is switched off by configuration, and nothing is warned or deleted. */
export interface ScheduledDeletions {
  enabled: boolean;
  days: number;
  warningDays: number;
  items: ScheduledDeletion[];
}

export interface NewSubprocessorNotice {
  name: string;
  purpose: string;
  dataCategories: string;
  location: string;
  /** ISO date (`yyyy-mm-dd`) the sub-processor starts; at least 30 days ahead. */
  effectiveFrom: string;
}

export interface SubprocessorNotice extends NewSubprocessorNotice {
  id: string;
  sentAt: string;
  sentBy: string | null;
  recipientCount: number;
  missingCount: number;
  /** Workspaces it could not be sent to (mail or workspace error). */
  failedCount: number;
}
