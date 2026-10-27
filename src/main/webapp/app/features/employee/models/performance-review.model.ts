import { AuditFields } from 'app/core/api';

import type { ReviewForm } from './performance-framework.model';
import { ReviewCyclePhase, ReviewCycleType } from './review-cycle.model';

/** Where a review stands in its cycle; reviews written outside a cycle are `SHARED`. */
/** `EXCLUDED`: taken out of its cycle (by HR, or because the employee left); HR can put it back while the cycle runs. */
export type PerformanceReviewStatus = 'NOT_STARTED' | 'SELF_SUBMITTED' | 'MANAGER_SUBMITTED' | 'SHARED' | 'EXCLUDED';

/** The employee's self-assessment (narrative only). */
export interface SelfAssessment {
  achievements: string | null;
  challenges: string | null;
  developmentGoals: string | null;
  comments: string | null;
  submittedAt: string;
}

/**
 * The reviewer's assessment; `rating` is theirs, the review's `rating` the final (possibly calibrated)
 * one. For the reviewed employee, `rating`, the promotion fields, `calibrationNote` and `potential`
 * are `null`.
 */
export interface ManagerAssessment {
  rating: number | null;
  strengths: string | null;
  areasForImprovement: string | null;
  developmentRecommendations: string | null;
  recommendForPromotion: boolean | null;
  promotionJustification: string | null;
  calibrationNote: string | null;
  /** Probation cycles only. */
  probationRecommendation: ProbationRecommendation | null;
  potential: ReviewPotential | null;
  submittedAt: string;
}

/** How far the employee could grow beyond their role — the 9-box's second axis. Confidential. */
export type ReviewPotential = 'LOW' | 'MODERATE' | 'HIGH';

export const REVIEW_POTENTIALS: ReviewPotential[] = ['LOW', 'MODERATE', 'HIGH'];

/** The reviewer's end-of-probation recommendation — a recommendation only. Confidential. */
export type ProbationRecommendation = 'CONFIRM' | 'EXTEND' | 'NOT_CONFIRM';

export const PROBATION_RECOMMENDATIONS: ProbationRecommendation[] = ['CONFIRM', 'EXTEND', 'NOT_CONFIRM'];

/**
 * The employee's acknowledgement of their shared review — read, not necessarily agreed. When
 * `disputed`, `comment` is why, and HR's `resolution` follows.
 */
export interface ReviewAcknowledgement {
  acknowledgedAt: string;
  comment: string | null;
  disputed: boolean;
  resolution: string | null;
  resolvedAt: string | null;
  resolvedByName: string | null;
}

/**
 * Second-level sign-off of a written review, in a cycle that requires one. PENDING and RETURNED
 * reviews can't be calibrated or shared; WAIVED = nobody above the reviewer to sign off.
 */
export type SignOffStatus = 'PENDING' | 'APPROVED' | 'RETURNED' | 'WAIVED';

/** Where the review stands at sign-off and the approver's last decision. Never sent to the reviewed employee. */
export interface ReviewSignOff {
  status: SignOffStatus;
  comment: string | null;
  decidedAt: string | null;
  decidedByName: string | null;
}

/** Why and when a review left its cycle; `excludedByName` is `null` when the employee's leaving excluded it. */
export interface ReviewExclusion {
  excludedAt: string;
  reason: string | null;
  excludedByName: string | null;
}

/** `GET /review-cycles/reviews/{id}/sign-off` — on whom the sign-off waits; `approvalRequestId` is what the approver decides on. */
export interface ReviewSignOffDetail {
  status: SignOffStatus | null;
  approvalRequestId: string | null;
  approverId: string | null;
  approverName: string | null;
  level: number;
  totalLevels: number;
  submittedAt: string | null;
  dueDate: string | null;
  canDecide: boolean;
}

/** `GET /api/hr/performance-reviews` (list) and `/{id}` (detail) — same shape. */
export interface PerformanceReview extends AuditFields {
  id: string;
  employeeId: string;
  employeeName: string | null;
  employeeJobTitle: string | null;
  departmentName: string | null;
  /** Empty on a cycle review until HR assigns a reviewer. */
  reviewerId: string | null;
  reviewerName: string | null;
  reviewDate: string;
  /** The reviewer's comments; hidden from the employee until the review is shared. */
  comments: string | null;
  /** Final rating, 1–5; hidden from the employee until the review is shared. */
  rating: number | null;
  cycleId: string | null;
  cycleName: string | null;
  cycleType: ReviewCycleType | null;
  cyclePhase: ReviewCyclePhase | null;
  /** The running cycle's deadline for its current phase. */
  phaseDeadline: string | null;
  status: PerformanceReviewStatus;
  selfAssessment: SelfAssessment | null;
  managerAssessment: ManagerAssessment | null;
  feedbackNotes: string | null;
  sharedAt: string | null;
  /** `null` until the employee acknowledges the shared review. */
  acknowledgement: ReviewAcknowledgement | null;
  /** `null` without sign-off, and always for the reviewed employee. */
  signOff: ReviewSignOff | null;
  /** Set on an excluded review, except for the reviewed employee. */
  exclusion?: ReviewExclusion | null;
  /** The template part, on a single review only; `null` (or absent) for the standard form. */
  form?: ReviewForm | null;
}

export interface CreatePerformanceReviewRequest {
  employeeId: string;
  reviewerId: string;
  reviewDate: string;
  comments?: string;
  /** 1–5. */
  rating: number;
}

export interface UpdatePerformanceReviewRequest {
  reviewDate?: string;
  comments?: string;
  /** 0–5. */
  rating?: number;
}

/** Criteria for `GET /api/hr/performance-reviews/search` (query params). */
export interface PerformanceReviewSearchRequest {
  employeeId?: string;
  reviewerId?: string;
  reviewDateFrom?: string;
  reviewDateTo?: string;
  minRating?: number;
  maxRating?: number;
  comments?: string;
  createdBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
  cycleId?: string;
  status?: PerformanceReviewStatus;
}
