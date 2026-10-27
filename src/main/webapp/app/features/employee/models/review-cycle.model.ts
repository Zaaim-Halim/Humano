/**
 * Review cycles — `/api/hr/workflows/review-cycles` — and the company's review calendar —
 * `/api/hr/workflows/review-calendar`.
 *
 * A cycle runs DRAFT → SELF_ASSESSMENT → MANAGER_REVIEW → (CALIBRATION) → FEEDBACK_DELIVERY →
 * COMPLETED → ARCHIVED. Starting the self-assessment creates one review per employee in scope;
 * closing shares every finished review that wasn't shared in a feedback meeting.
 */
import type { GoalRatingInput } from './goal.model';
import type { CompetencyRatingInput, ReviewAnswerInput } from './performance-framework.model';
import type { ProbationRecommendation, ReviewPotential } from './performance-review.model';

export type ReviewCyclePhase =
  | 'DRAFT'
  | 'SELF_ASSESSMENT'
  | 'MANAGER_REVIEW'
  | 'CALIBRATION'
  | 'FEEDBACK_DELIVERY'
  | 'GOAL_SETTING'
  | 'COMPLETED'
  | 'ARCHIVED';

/** The phases a cycle walks through, in order (goal setting isn't used by the review flow). */
export const REVIEW_CYCLE_PHASES: ReviewCyclePhase[] = [
  'DRAFT',
  'SELF_ASSESSMENT',
  'MANAGER_REVIEW',
  'CALIBRATION',
  'FEEDBACK_DELIVERY',
  'COMPLETED',
];

/** The five-point rating scale, 1 (doesn't meet expectations) to 5 (outstanding). */
export const RATING_SCALE = [1, 2, 3, 4, 5] as const;

export interface ReviewPhaseDeadlines {
  selfAssessmentDeadline: string | null;
  managerReviewDeadline: string | null;
  /** `null` when the cycle has no calibration. */
  calibrationDeadline: string | null;
  feedbackDeadline: string | null;
}

export interface ReviewDepartmentProgress {
  departmentId: string | null;
  departmentName: string | null;
  totalEmployees: number;
  completedReviews: number;
  completionPercentage: number;
}

export interface ReviewCycleProgress {
  totalEmployees: number;
  completedSelfAssessments: number;
  completedManagerReviews: number;
  /** Reviews shared with the employee. */
  deliveredFeedbacks: number;
  /** Against the current phase's own step (self-assessments while they're due, and so on). */
  completionPercentage: number;
  /** Only on the single-cycle reads. */
  departmentProgress: ReviewDepartmentProgress[] | null;
  /** Feedback requests sent (answered or not); only on the single-cycle reads. */
  feedbackRequested: number | null;
  /** Feedback received from contributors; only on the single-cycle reads. */
  feedbackReceived: number | null;
  /** Reviews the employee acknowledged; only on the single-cycle reads. */
  acknowledged: number | null;
  /** Disputed reviews waiting for HR; only on the single-cycle reads. */
  disputesOpen: number | null;
  /** Reviews waiting for sign-off; only on single-cycle reads of a cycle requiring it. */
  signOffPending: number | null;
  /** Reviews returned to the reviewer at sign-off; as above. */
  signOffReturned: number | null;
}

/**
 * Who sees contributors' feedback once a review is shared. The reviewer and HR always see it,
 * with names; anonymous feedback is shown only from three answers.
 */
export type FeedbackVisibility = 'MANAGER_ONLY' | 'SHARED_NAMED' | 'SHARED_ANONYMOUS';

export const FEEDBACK_VISIBILITIES: FeedbackVisibility[] = ['MANAGER_ONLY', 'SHARED_NAMED', 'SHARED_ANONYMOUS'];

/** A cycle's additional-feedback settings. `maxContributors` 0 = no additional feedback. */
export interface ReviewFeedbackSettings {
  visibility: FeedbackVisibility;
  allowNominations: boolean;
  maxContributors: number;
}

/**
 * REGULAR: the calendar's cycles, for departments or the company (no two at once for the same
 * people). PROBATION: employees not yet confirmed whose probation ends in the review period.
 * AD_HOC: named employees. Probation and ad hoc cycles may run alongside any other.
 */
export type ReviewCycleType = 'REGULAR' | 'PROBATION' | 'AD_HOC';

export const REVIEW_CYCLE_TYPES: ReviewCycleType[] = ['REGULAR', 'PROBATION', 'AD_HOC'];

/** An employee an ad hoc cycle reviews. */
export interface ReviewCycleEmployee {
  id: string;
  name: string;
}

export interface ReviewCycle {
  cycleId: string;
  workflowId: string | null;
  name: string;
  description: string | null;
  reviewPeriodStart: string;
  reviewPeriodEnd: string;
  startDate: string;
  endDate: string;
  phase: ReviewCyclePhase;
  /** The phase going back would reopen, on a single cycle; `null` in lists or when it can't go back. */
  previousPhase: ReviewCyclePhase | null;
  deadlines: ReviewPhaseDeadlines;
  progress: ReviewCycleProgress;
  feedback: ReviewFeedbackSettings;
  /** Each written review needs a second-level sign-off before calibration and sharing. */
  requiresSignOff: boolean;
  type: ReviewCycleType;
  /** Empty = company-wide (and always for an ad hoc cycle). */
  departmentIds: string[];
  /** An ad hoc cycle's employees; empty for the other types. */
  employees: ReviewCycleEmployee[];
  /** The review template; `null` for the standard form. */
  template: { id: string; name: string } | null;
  active: boolean;
  createdDate: string | null;
  lastModifiedDate: string | null;
}

/** `POST /` and `PUT /{id}` (draft only). No departments = company-wide. */
export interface SaveReviewCycleRequest {
  name: string;
  description?: string;
  reviewPeriodStart: string;
  reviewPeriodEnd: string;
  startDate: string;
  endDate: string;
  selfAssessmentDeadline: string;
  managerReviewDeadline: string;
  calibrationDeadline?: string | null;
  feedbackDeadline: string;
  departmentIds: string[];
  includeAllDepartments: boolean;
  feedbackVisibility: FeedbackVisibility;
  allowFeedbackNominations: boolean;
  /** 0–20; 0 = no additional feedback. */
  maxContributors: number;
  requiresSignOff: boolean;
  type: ReviewCycleType;
  /** AD_HOC only, and required there. */
  employeeIds: string[];
  /** Omitted or `null` for the standard form. */
  templateId?: string | null;
}

/** `POST /{cycleId}/self-assessment` — narrative only; achievements are required. */
export interface SelfAssessmentRequest {
  achievements: string;
  challenges?: string;
  developmentGoals?: string;
  additionalComments?: string;
  /** Answers to the template's employee questions. */
  answers?: ReviewAnswerInput[];
}

/** `POST /{cycleId}/manager-review`. */
export interface ManagerReviewRequest {
  employeeId: string;
  /** 1–5. */
  rating: number;
  strengths: string;
  areasForImprovement?: string;
  managerComments?: string;
  developmentRecommendations?: string;
  recommendForPromotion: boolean;
  promotionJustification?: string;
  potential?: ReviewPotential;
  /** Required in a probation cycle; ignored otherwise. */
  probationRecommendation?: ProbationRecommendation;
  /** Every template competency, rated 1–5. */
  competencyRatings?: CompetencyRatingInput[];
  /** Answers to the template's reviewer questions. */
  answers?: ReviewAnswerInput[];
  /** How far each of the review's goals was achieved — every one, when it has goals. */
  goalRatings?: GoalRatingInput[];
}

/** `POST /reviews/{reviewId}/acknowledgement` — a dispute needs a comment saying why. */
export interface AcknowledgeReviewRequest {
  comment?: string | null;
  disputed: boolean;
}

/** `POST /reviews/{reviewId}/dispute-resolution` (HR) — a different rating replaces the final one. */
export interface ResolveDisputeRequest {
  resolution: string;
  rating?: number | null;
}

export type RatingChangeSource = 'CALIBRATION' | 'DISPUTE_RESOLUTION';

/** `GET /reviews/{reviewId}/rating-changes` (HR) — the final rating's changes after the reviewer gave it. */
export interface RatingChange {
  id: string;
  fromRating: number | null;
  toRating: number;
  source: RatingChangeSource;
  reason: string | null;
  changedById: string | null;
  changedByName: string | null;
  changedAt: string;
}

/** `GET /{cycleId}/addable-employees` — someone HR can add to a running cycle. */
export interface CycleCandidate {
  id: string;
  name: string;
  jobTitle: string | null;
  departmentName: string | null;
}

/**
 * `POST /{cycleId}/phases/back` — reopens the previous phase. A deadline given replaces the
 * reopened phase's (or a later one's); the reopened phase's must be today or later.
 */
export interface MoveBackPhaseRequest {
  reason: string;
  selfAssessmentDeadline?: string | null;
  managerReviewDeadline?: string | null;
  calibrationDeadline?: string | null;
  feedbackDeadline?: string | null;
}

/** `PUT /{cycleId}/reviews/{reviewId}/calibration` — a note is required when the rating changes. */
export interface CalibrateReviewRequest {
  rating: number;
  note?: string;
  /** Replaces the review's potential; left out, it stays. */
  potential?: ReviewPotential;
}

export type ReviewCadence = 'ANNUAL' | 'SEMI_ANNUAL';

/** The next cycle the calendar proposes; pre-fills the new-cycle form. */
export interface ReviewCycleProposal {
  name: string;
  reviewPeriodStart: string;
  reviewPeriodEnd: string;
  startDate: string;
  endDate: string;
  selfAssessmentDeadline: string;
  managerReviewDeadline: string;
  calibrationDeadline: string | null;
  feedbackDeadline: string;
}

/** `GET /api/hr/workflows/review-calendar` — defaults until saved (`customized: false`). */
export interface ReviewCalendar {
  cadence: ReviewCadence;
  /** 1–12; 1 = calendar year. */
  yearStartMonth: number;
  opensDaysBeforePeriodEnd: number;
  selfAssessmentDays: number;
  managerReviewDays: number;
  /** 0 = no calibration. */
  calibrationDays: number;
  feedbackDays: number;
  customized: boolean;
  nextCycle: ReviewCycleProposal;
}

export type UpdateReviewCalendarRequest = Omit<ReviewCalendar, 'customized' | 'nextCycle'>;
