import { ReviewCyclePhase, ReviewCycleType } from 'app/features/employee';

/**
 * Retention of performance review records — `/api/hr/workflows/review-retention`
 * (`CONFIGURE_PERFORMANCE_CYCLES`).
 */

/** What keeps a cycle past the cutoff. */
export interface RetentionHolds {
  notArchived: boolean;
  openDisputes: number;
  openPlans: number;
  pendingProposals: number;
}

export interface RetentionCycle {
  id: string;
  name: string;
  type: ReviewCycleType;
  phase: ReviewCyclePhase;
  periodStart: string;
  periodEnd: string;
  reviews: number;
  holds: RetentionHolds;
}

/** `GET /` — the settings (defaults until saved: `customized: false`) and what they mean today. */
export interface ReviewRetention {
  enabled: boolean;
  retainYears: number;
  customized: boolean;
  /** Review periods that ended before this date are past the retention period. */
  cutoff: string;
  /** Archived and settled: deleted on the next run. */
  due: RetentionCycle[];
  held: RetentionCycle[];
}

export type ReviewRetentionSettings = Pick<ReviewRetention, 'enabled' | 'retainYears'>;

/** `POST /purge` — what was deleted; `failed` cycles were left whole. */
export interface ReviewRetentionPurgeResult {
  cycles: number;
  reviews: number;
  failed: number;
}
