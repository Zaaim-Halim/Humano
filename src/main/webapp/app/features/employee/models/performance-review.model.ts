import { AuditFields } from 'app/core/api';

/** `GET /api/hr/performance-reviews` (list) and `/{id}` (detail) — same shape. */
export interface PerformanceReview extends AuditFields {
  id: string;
  employeeId: string;
  employeeName: string | null;
  reviewerId: string;
  reviewerName: string | null;
  reviewDate: string;
  comments: string | null;
  /** 1–5. */
  rating: number | null;
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
}
