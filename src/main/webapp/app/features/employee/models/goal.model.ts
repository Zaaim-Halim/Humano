/**
 * Goals — `/api/hr/goals`: what each employee aims to achieve, agreed with their manager and checked
 * in on along the way. The review whose period covers a goal's due date rates how far it was achieved.
 */

/** DRAFT is the employee's proposal; agreeing it (or a goal the manager sets) makes it ACTIVE. */
export type GoalStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export const GOAL_STATUSES: GoalStatus[] = ['DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED'];

/** The reviewer's goal scale — separate from the 1–5 performance rating. */
export type GoalAchievement = 'NOT_MET' | 'PARTIALLY_MET' | 'MET' | 'EXCEEDED';

export const GOAL_ACHIEVEMENTS: GoalAchievement[] = ['NOT_MET', 'PARTIALLY_MET', 'MET', 'EXCEEDED'];

/** What the signed-in user may do with a goal, worked out by the server. */
export interface GoalActions {
  edit: boolean;
  agree: boolean;
  checkIn: boolean;
  complete: boolean;
  cancel: boolean;
  delete: boolean;
}

export interface Goal {
  id: string;
  employeeId: string;
  employeeName: string;
  title: string;
  description: string | null;
  successMeasure: string | null;
  /** Share of the employee's goals in percent; optional. */
  weight: number | null;
  startDate: string;
  dueDate: string;
  status: GoalStatus;
  /** 0–100, from the latest check-in. */
  progress: number;
  agreedAt: string | null;
  agreedByName: string | null;
  completedAt: string | null;
  cancelReason: string | null;
  /** Rated in a submitted review: can no longer be edited, cancelled or deleted. */
  locked: boolean;
  can: GoalActions;
}

/** A new or changed goal. Without `employeeId` it is the signed-in employee's own. */
export interface SaveGoalRequest {
  employeeId?: string | null;
  title: string;
  description?: string | null;
  successMeasure?: string | null;
  weight?: number | null;
  startDate: string;
  dueDate: string;
}

/** A direct report of the signed-in manager. */
export interface GoalTeamMember {
  id: string;
  name: string;
}

export interface GoalCheckIn {
  id: string;
  progress: number;
  comment: string | null;
  authorName: string | null;
  checkedInAt: string;
}

export interface GoalCheckInRequest {
  progress: number;
  comment?: string | null;
}

/** A goal on a review's form; `achievement` and `comment` are the reviewer's. */
export interface ReviewFormGoal {
  id: string;
  title: string;
  successMeasure: string | null;
  weight: number | null;
  dueDate: string;
  status: GoalStatus;
  progress: number;
  achievement: GoalAchievement | null;
  comment: string | null;
}

/** The reviewer's rating of a goal, sent with the manager review. */
export interface GoalRatingInput {
  goalId: string;
  achievement: GoalAchievement;
  comment?: string;
}

/** Points per achievement level, for the weighted guidance shown to the reviewer (never the rating). */
export const GOAL_ACHIEVEMENT_SCORE: Record<GoalAchievement, number> = { NOT_MET: 0, PARTIALLY_MET: 50, MET: 100, EXCEEDED: 120 };
