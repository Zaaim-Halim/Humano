/**
 * Performance analytics — `/api/hr/performance-analytics`: counts per cycle and department, newest
 * cycle first. Rates are computed here from the counts, each on its own base.
 */
import { ReviewCyclePhase, ReviewCycleType } from 'app/features/employee';

export interface PerformanceStats {
  reviews: number;
  selfSubmitted: number;
  managerSubmitted: number;
  shared: number;
  /** Of `shared`. */
  acknowledged: number;
  /** Of `shared`. */
  disputed: number;
  /** Written, with a final rating: the base of the distribution and the rates below. */
  rated: number;
  /** Final ratings 1 to 5. */
  distribution: number[];
  averageRating: number | null;
  raised: number;
  lowered: number;
  promotionRecommended: number;
  withImprovementPlan: number;
}

export interface CycleStats {
  cycleId: string;
  name: string;
  phase: ReviewCyclePhase;
  reviewPeriodStart: string;
  reviewPeriodEnd: string;
  overall: PerformanceStats;
  departments: { departmentId: string | null; name: string | null; stats: PerformanceStats }[];
}

export interface PerformanceAnalytics {
  type: ReviewCycleType;
  cycles: CycleStats[];
}
