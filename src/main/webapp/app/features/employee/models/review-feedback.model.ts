import { FeedbackVisibility } from './review-cycle.model';

/**
 * Additional reviewers on a cycle review: colleagues asked for feedback by the reviewer or HR, or
 * nominated by the reviewed employee (the reviewer approves). Narrative only, no rating.
 */
export type FeedbackRelationship =
  | 'PEER'
  | 'DIRECT_REPORT'
  | 'PROJECT_LEAD'
  | 'SKIP_LEVEL'
  | 'CROSS_FUNCTIONAL'
  | 'PREVIOUS_MANAGER'
  | 'OTHER';

/** The relationships offered when asking someone (a previous manager is added when a review is reassigned). */
export const FEEDBACK_RELATIONSHIPS: FeedbackRelationship[] = [
  'PEER',
  'DIRECT_REPORT',
  'PROJECT_LEAD',
  'SKIP_LEVEL',
  'CROSS_FUNCTIONAL',
  'PREVIOUS_MANAGER',
  'OTHER',
];

export type FeedbackRequestStatus = 'PENDING_APPROVAL' | 'REQUESTED' | 'SUBMITTED' | 'DECLINED' | 'CANCELLED';

/**
 * A feedback request, with the feedback once given. Fields the viewer may not see are `null`; an
 * anonymous contribution shown to the reviewed employee has no `id`, contributor, relationship or dates.
 */
export interface ReviewFeedback {
  id: string | null;
  reviewId: string;
  cycleId: string;
  cycleName: string;
  employeeId: string;
  employeeName: string;
  employeeJobTitle: string | null;
  contributorId: string | null;
  contributorName: string | null;
  contributorJobTitle: string | null;
  relationship: FeedbackRelationship | null;
  status: FeedbackRequestStatus;
  requestedById: string | null;
  requestedByName: string | null;
  requestedAt: string | null;
  dueDate: string | null;
  message: string | null;
  strengths: string | null;
  improvements: string | null;
  comments: string | null;
  submittedAt: string | null;
  declineReason: string | null;
  /** Whether the reviewed employee will see it once the review is shared (the cycle's setting). */
  visibility: FeedbackVisibility;
  /** The contributor can still give or revise their feedback. */
  open: boolean;
}

/** `GET /{cycleId}/reviews/{reviewId}/feedback-requests` — as the caller may see them. */
export interface ReviewFeedbackOverview {
  visibility: FeedbackVisibility;
  /** Shown without names (the reviewed employee, in an anonymous cycle). */
  anonymous: boolean;
  /** Anonymous feedback hidden: fewer than `minimumResponses` answers. */
  withheld: boolean;
  minimumResponses: number;
  maxContributors: number;
  activeContributors: number;
  canRequest: boolean;
  canNominate: boolean;
  canManage: boolean;
  requests: ReviewFeedback[];
}

/** `GET /{cycleId}/feedback-counts` — only reviews with requests. */
export interface ReviewFeedbackCount {
  reviewId: string;
  awaitingApproval: number;
  requested: number;
  received: number;
}

export interface RequestFeedbackRequest {
  contributorId: string;
  relationship: FeedbackRelationship;
  dueDate?: string | null;
  message?: string | null;
}

/** Strengths or improvements are required. */
export interface SubmitFeedbackRequest {
  strengths?: string | null;
  improvements?: string | null;
  comments?: string | null;
}

/** A colleague who could be asked for feedback: directory details only. */
export interface FeedbackCandidate {
  id: string;
  name: string;
  jobTitle: string | null;
  departmentName: string | null;
}
