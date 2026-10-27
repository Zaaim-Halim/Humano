/**
 * Performance improvement plans — `/api/hr/improvement-plans`. Started by the employee's manager or HR
 * from a shared review rated 2 or lower; private to them while a draft; acknowledged by the employee
 * once shared; closed with an outcome that is recorded only (it never changes the employee's status).
 */

export type ImprovementPlanStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED';

export type ImprovementPlanOutcome = 'SUCCESSFUL' | 'UNSUCCESSFUL' | 'CANCELLED';

export const IMPROVEMENT_PLAN_OUTCOMES: ImprovementPlanOutcome[] = ['SUCCESSFUL', 'UNSUCCESSFUL', 'CANCELLED'];

export type ImprovementPlanProgress = 'ON_TRACK' | 'SOME_PROGRESS' | 'NOT_ON_TRACK';

export const IMPROVEMENT_PLAN_PROGRESS: ImprovementPlanProgress[] = ['ON_TRACK', 'SOME_PROGRESS', 'NOT_ON_TRACK'];

/** How long a plan runs, and how much one extension may add — as the server enforces. */
export const IMPROVEMENT_PLAN_LIMITS = { minDays: 30, maxDays: 180, maxExtensionDays: 90, maxObjectives: 10 };

export interface ImprovementPlanObjective {
  id?: string;
  description: string;
  successMeasure: string | null;
  dueDate: string | null;
}

export interface ImprovementPlanCheckIn {
  id: string;
  progress: ImprovementPlanProgress;
  note: string;
  authorName: string | null;
  checkedInAt: string;
}

/** What the signed-in user may do with a plan, worked out by the server. */
export interface ImprovementPlanActions {
  edit: boolean;
  activate: boolean;
  delete: boolean;
  checkIn: boolean;
  extend: boolean;
  close: boolean;
  acknowledge: boolean;
}

export interface ImprovementPlan {
  id: string;
  employeeId: string;
  employeeName: string;
  employeeJobTitle: string | null;
  ownerName: string | null;
  reviewId: string;
  cycleName: string | null;
  /** The review's final rating the plan was started from. */
  reviewRating: number | null;
  status: ImprovementPlanStatus;
  concerns: string;
  support: string | null;
  meetingCadence: string | null;
  startDate: string;
  endDate: string;
  /** The end date before the plan was extended; `null` unless it was. */
  originalEndDate: string | null;
  extensionReason: string | null;
  activatedAt: string | null;
  acknowledgedAt: string | null;
  employeeComment: string | null;
  outcome: ImprovementPlanOutcome | null;
  outcomeNote: string | null;
  closedAt: string | null;
  closedByName: string | null;
  /** Empty in lists. */
  objectives: ImprovementPlanObjective[];
  /** Newest first; empty in lists. */
  checkIns: ImprovementPlanCheckIn[];
  can: ImprovementPlanActions;
}

export interface SaveImprovementPlanRequest {
  concerns: string;
  support?: string | null;
  meetingCadence?: string | null;
  startDate: string;
  endDate: string;
  objectives: { description: string; successMeasure?: string | null; dueDate?: string | null }[];
}

/** From a review: whether a plan can start now, and the latest plan started from it that the user may see. */
export interface ReviewImprovementPlanStatus {
  canStart: boolean;
  planId: string | null;
  status: ImprovementPlanStatus | null;
}
