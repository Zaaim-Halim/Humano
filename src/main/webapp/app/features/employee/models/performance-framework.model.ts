import { ReviewFormGoal } from './goal.model';

/**
 * The performance framework — `/api/hr/performance-frameworks`: competencies (with what each
 * rating level looks like) and the review templates built from them. A cycle picks a template while
 * it is a draft and copies it when it opens, so editing a template never changes a running cycle.
 */

export type CompetencyCategory = 'VALUE' | 'CORE' | 'LEADERSHIP' | 'FUNCTIONAL';

export const COMPETENCY_CATEGORIES: CompetencyCategory[] = ['VALUE', 'CORE', 'LEADERSHIP', 'FUNCTIONAL'];

/** Who answers a template question: the employee in their self-assessment, or the reviewer. */
export type ReviewQuestionAudience = 'EMPLOYEE' | 'REVIEWER';

export const REVIEW_QUESTION_AUDIENCES: ReviewQuestionAudience[] = ['EMPLOYEE', 'REVIEWER'];

export interface Competency {
  id: string;
  name: string;
  description: string | null;
  category: CompetencyCategory;
  /** What rating levels 1–5 look like, in order; blank levels are `null`. */
  anchors: (string | null)[];
  /** A retired competency stays on its templates but can't be added to others. */
  active: boolean;
  /** The templates using it (can't be deleted while used). */
  usedBy: string[];
}

export interface SaveCompetencyRequest {
  name: string;
  description?: string | null;
  category: CompetencyCategory;
  anchors: string[];
  active?: boolean;
}

export interface ReviewTemplateQuestion {
  audience: ReviewQuestionAudience;
  text: string;
  helpText: string | null;
  required: boolean;
}

export interface ReviewTemplate {
  id: string;
  name: string;
  description: string | null;
  /** A retired template stays on its cycles but can't be picked for new ones. */
  active: boolean;
  competencies: Competency[];
  questions: ReviewTemplateQuestion[];
  /** How many cycles use it (can't be deleted while used). */
  cycles: number;
}

export interface SaveReviewTemplateRequest {
  name: string;
  description?: string | null;
  competencyIds: string[];
  questions: ReviewTemplateQuestion[];
  active?: boolean;
}

/**
 * The part of a review beyond the standard form: what comes from its cycle's template (copied when the
 * cycle opened) and the goals it rates, with the answers so far. For the reviewed employee, the reviewer's answers and ratings are left out
 * until the review is shared.
 */
export interface ReviewForm {
  templateName: string | null;
  competencies: ReviewFormCompetency[];
  questions: ReviewFormQuestion[];
  answers: ReviewFormAnswer[];
  ratings: ReviewFormRating[];
  /** The employee's goals the review rates (due within the period). */
  goals: ReviewFormGoal[];
}

export interface ReviewFormCompetency {
  id: string;
  name: string;
  description: string | null;
  category: CompetencyCategory;
  anchors: (string | null)[];
}

export interface ReviewFormQuestion {
  id: string;
  audience: ReviewQuestionAudience;
  text: string;
  helpText: string | null;
  required: boolean;
}

export interface ReviewFormAnswer {
  questionId: string;
  answer: string;
}

export interface ReviewFormRating {
  competencyId: string;
  rating: number;
  comment: string | null;
}

/** An answer sent with the self-assessment or the review; blank removes an earlier one. */
export interface ReviewAnswerInput {
  questionId: string;
  answer: string;
}

/** The reviewer's rating of a competency, 1–5. */
export interface CompetencyRatingInput {
  competencyId: string;
  rating: number;
  comment?: string;
}
