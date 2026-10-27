/**
 * Compensation review — `/api/hr/compensation-reviews`: pay and promotion proposals following a
 * regular review cycle, and the merit matrix that guides them. Needs `MANAGE_COMPENSATION`.
 */
import { ReviewCyclePhase } from 'app/features/employee';

export type CompensationProposalStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'APPLIED';
export type PayBasis = 'MONTHLY' | 'ANNUAL' | 'HOURLY';

export const COMPENSATION_PROPOSAL_STATUSES: CompensationProposalStatus[] = ['DRAFT', 'SUBMITTED', 'APPROVED', 'APPLIED'];

export interface CompensationProposalActions {
  edit: boolean;
  delete: boolean;
  submit: boolean;
  approve: boolean;
  returnToDraft: boolean;
  apply: boolean;
}

/** One person's proposal. It carries the review's final rating, never its text. Amounts are BigDecimal JSON. */
export interface CompensationProposal {
  id: string;
  cycleId: string;
  reviewId: string;
  employeeId: string;
  employeeName: string;
  jobTitle: string | null;
  rating: number;
  promotionRecommended: boolean;
  status: CompensationProposalStatus;
  effectiveDate: string;
  currentSalary: number | null;
  currencyCode: string | null;
  basis: PayBasis | null;
  currentPositionId: string | null;
  currentPositionName: string | null;
  newSalary: number | null;
  /** Base pay increase in percent; null without a current or new salary. */
  increasePercent: number | null;
  guidanceMin: number;
  guidanceMax: number;
  /** The increase falls outside the merit matrix's range — a flag, never a block. */
  outsideGuidance: boolean;
  newPositionId: string | null;
  newPositionName: string | null;
  bonusAmount: number | null;
  justification: string | null;
  returnComment: string | null;
  submittedByName: string | null;
  submittedAt: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  appliedByName: string | null;
  appliedAt: string | null;
  /** The pay or position in effect changed since it was prepared: back to draft and save again. */
  payChanged: boolean;
  actions: CompensationProposalActions;
}

export interface CompensationTotal {
  currencyCode: string;
  basis: PayBasis;
  count: number;
  current: number;
  proposed: number;
  increasePercent: number | null;
  bonuses: number;
}

export interface CompensationReview {
  cycleId: string;
  cycleName: string;
  phase: ReviewCyclePhase;
  reviewPeriodStart: string;
  reviewPeriodEnd: string;
  /** Shared, rated reviews without a proposal yet. */
  eligible: number;
  /** Reviews not shared or rated yet. */
  waiting: number;
  /** Shared reviews with an open dispute. */
  disputed: number;
  proposals: CompensationProposal[];
  totals: CompensationTotal[];
}

export interface CompensationReviewCycle {
  id: string;
  name: string;
  phase: ReviewCyclePhase;
  reviewPeriodStart: string;
  reviewPeriodEnd: string;
  proposals: number;
}

export interface SaveCompensationProposalRequest {
  newSalary: number | null;
  newPositionId: string | null;
  bonusAmount: number | null;
  effectiveDate: string;
  justification: string | null;
}

export interface MeritBand {
  rating: number;
  minPercent: number;
  maxPercent: number;
}

export interface MeritGuidelines {
  bands: MeritBand[];
}
