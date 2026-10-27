import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { PerformanceReview, PerformanceReviewStatus, ReviewSignOffDetail } from '../models/performance-review.model';
import {
  AcknowledgeReviewRequest,
  CalibrateReviewRequest,
  CycleCandidate,
  ManagerReviewRequest,
  MoveBackPhaseRequest,
  RatingChange,
  ResolveDisputeRequest,
  ReviewCycle,
  ReviewCycleProgress,
  SaveReviewCycleRequest,
  SelfAssessmentRequest,
} from '../models/review-cycle.model';

/** The phase a cycle can be moved into from the cycle page, and its endpoint segment. */
export type ReviewPhaseStep = 'self-assessment' | 'manager-review' | 'calibration' | 'feedback' | 'goal-setting';

/**
 * Review cycles — `/api/hr/workflows/review-cycles`.
 *
 * - Cycles: reads need `VIEW_PERFORMANCE_REVIEWS`; creating, editing a draft, moving phases,
 *   reminders, closing and archiving need `CONFIGURE_PERFORMANCE_CYCLES`.
 * - Reviews: the signed-in employee's own (`my-reviews`), the ones they write (`my-team-reviews`)
 *   and a single one (employee, reviewer or HR). Writing is checked per review: the employee
 *   writes their self-assessment, the reviewer (or HR with `MANAGE_PERFORMANCE_REVIEWS`) the
 *   manager review and the feedback meeting; calibration needs `MANAGE_CALIBRATION`. Once shared,
 *   the employee acknowledges (or disputes) their review; HR resolves disputes.
 * - Membership (`CONFIGURE_PERFORMANCE_CYCLES`): while the cycle runs, HR takes a review out of
 *   it (with a reason) and puts it back, and adds people who joined late, until manager reviews
 *   end. A phase started too early can be moved back.
 */
@Injectable({ providedIn: 'root' })
export class ReviewCycleService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/review-cycles');
  private readonly approvalsUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/approvals');

  /** `GET /` — every cycle, newest first by default. */
  list(req?: PageRequest): Observable<Page<ReviewCycle>> {
    return this.http.get<Page<ReviewCycle>>(this.resourceUrl, { params: createRequestOption(req) });
  }

  /** `GET /{id}` — one cycle with progress per department. */
  find(cycleId: string): Observable<ReviewCycle> {
    return this.http.get<ReviewCycle>(this.url(cycleId));
  }

  create(body: SaveReviewCycleRequest): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(this.resourceUrl, body);
  }

  /** `PUT /{id}` — drafts only. */
  update(cycleId: string, body: SaveReviewCycleRequest): Observable<ReviewCycle> {
    return this.http.put<ReviewCycle>(this.url(cycleId), body);
  }

  /** `POST /{id}/phases/{step}/start`. */
  startPhase(cycleId: string, step: ReviewPhaseStep): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.url(cycleId)}/phases/${step}/start`, null);
  }

  /** `POST /{id}/close` — shares every finished review not shared yet. */
  close(cycleId: string): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.url(cycleId)}/close`, null);
  }

  archive(cycleId: string): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.url(cycleId)}/archive`, null);
  }

  /** `POST /{id}/reminders` — reminds whoever still has something to do in the current phase. */
  remind(cycleId: string): Observable<void> {
    return this.http.post<void>(`${this.url(cycleId)}/reminders`, null);
  }

  progress(cycleId: string): Observable<ReviewCycleProgress> {
    return this.http.get<ReviewCycleProgress>(`${this.url(cycleId)}/progress`);
  }

  /** `GET /{id}/reviews?status=` — the cycle's reviews (HR). */
  reviews(cycleId: string, status?: PerformanceReviewStatus, req?: PageRequest): Observable<Page<PerformanceReview>> {
    return this.http.get<Page<PerformanceReview>>(`${this.url(cycleId)}/reviews`, {
      params: createRequestOption({ ...req, ...(status ? { status } : {}) }),
    });
  }

  /** `GET /my-reviews` — the signed-in employee's reviews in running cycles. */
  myReviews(): Observable<PerformanceReview[]> {
    return this.http.get<PerformanceReview[]>(`${this.resourceUrl}/my-reviews`);
  }

  /** `GET /my-team-reviews` — reviews the signed-in employee writes, in running cycles. */
  reviewsToWrite(): Observable<PerformanceReview[]> {
    return this.http.get<PerformanceReview[]>(`${this.resourceUrl}/my-team-reviews`);
  }

  /** `GET /reviews/{reviewId}` — one review, as the caller may see it. */
  review(reviewId: string): Observable<PerformanceReview> {
    return this.http.get<PerformanceReview>(`${this.resourceUrl}/reviews/${encodeURIComponent(reviewId)}`);
  }

  /** `GET /reviews/{reviewId}/pdf` — a shared review as a PDF (always the employee's view). */
  reviewPdf(reviewId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.resourceUrl}/reviews/${encodeURIComponent(reviewId)}/pdf`, { observe: 'response', responseType: 'blob' });
  }

  submitSelfAssessment(cycleId: string, body: SelfAssessmentRequest): Observable<PerformanceReview> {
    return this.http.post<PerformanceReview>(`${this.url(cycleId)}/self-assessment`, body);
  }

  submitManagerReview(cycleId: string, body: ManagerReviewRequest): Observable<PerformanceReview> {
    return this.http.post<PerformanceReview>(`${this.url(cycleId)}/manager-review`, body);
  }

  /** `POST /{id}/feedback/{employeeId}` — records the meeting and shares the review. */
  recordFeedbackMeeting(cycleId: string, employeeId: string, notes?: string): Observable<PerformanceReview> {
    return this.http.post<PerformanceReview>(`${this.url(cycleId)}/feedback/${encodeURIComponent(employeeId)}`, notes ? { notes } : {});
  }

  calibrate(cycleId: string, reviewId: string, body: CalibrateReviewRequest): Observable<PerformanceReview> {
    return this.http.put<PerformanceReview>(`${this.url(cycleId)}/reviews/${encodeURIComponent(reviewId)}/calibration`, body);
  }

  /** The reviewed employee acknowledges their shared review, or disputes it. */
  acknowledge(reviewId: string, body: AcknowledgeReviewRequest): Observable<PerformanceReview> {
    return this.http.post<PerformanceReview>(`${this.resourceUrl}/reviews/${encodeURIComponent(reviewId)}/acknowledgement`, body);
  }

  /** HR resolves a disputed review (`MANAGE_PERFORMANCE_REVIEWS`). */
  resolveDispute(reviewId: string, body: ResolveDisputeRequest): Observable<PerformanceReview> {
    return this.http.post<PerformanceReview>(`${this.resourceUrl}/reviews/${encodeURIComponent(reviewId)}/dispute-resolution`, body);
  }

  /** The review's sign-off — for its reviewer, approvers and HR, never the reviewed employee. */
  signOff(reviewId: string): Observable<ReviewSignOffDetail> {
    return this.http.get<ReviewSignOffDetail>(`${this.resourceUrl}/reviews/${encodeURIComponent(reviewId)}/sign-off`);
  }

  /**
   * The approver signs the review off, or returns it to the reviewer (a comment saying what to
   * change is required) — through the approval it waits on.
   */
  decideSignOff(approvalRequestId: string, decision: 'APPROVE' | 'REJECT', comments?: string): Observable<void> {
    return this.http
      .post(`${this.approvalsUrl}/${encodeURIComponent(approvalRequestId)}/decide`, { decision, ...(comments ? { comments } : {}) })
      .pipe(map(() => undefined));
  }

  /** The final rating's changes after the reviewer gave it (`VIEW_PERFORMANCE_REVIEWS`). */
  ratingChanges(reviewId: string): Observable<RatingChange[]> {
    return this.http.get<RatingChange[]>(`${this.resourceUrl}/reviews/${encodeURIComponent(reviewId)}/rating-changes`);
  }

  assignReviewer(cycleId: string, reviewId: string, reviewerId: string): Observable<PerformanceReview> {
    return this.http.put<PerformanceReview>(`${this.url(cycleId)}/reviews/${encodeURIComponent(reviewId)}/reviewer`, { reviewerId });
  }

  /** `POST /{id}/phases/back` — reopens the previous phase. */
  moveBack(cycleId: string, body: MoveBackPhaseRequest): Observable<ReviewCycle> {
    return this.http.post<ReviewCycle>(`${this.url(cycleId)}/phases/back`, body);
  }

  /** `POST /{id}/reviews/{reviewId}/exclusion` — takes the review out of the cycle. */
  exclude(cycleId: string, reviewId: string, reason: string): Observable<PerformanceReview> {
    return this.http.post<PerformanceReview>(`${this.url(cycleId)}/reviews/${encodeURIComponent(reviewId)}/exclusion`, { reason });
  }

  /** `DELETE /{id}/reviews/{reviewId}/exclusion` — puts an excluded review back. */
  restore(cycleId: string, reviewId: string): Observable<PerformanceReview> {
    return this.http.delete<PerformanceReview>(`${this.url(cycleId)}/reviews/${encodeURIComponent(reviewId)}/exclusion`);
  }

  /** `GET /{id}/addable-employees?query=` — people in the cycle's scope not in it yet (the first 20). */
  addableEmployees(cycleId: string, query: string): Observable<CycleCandidate[]> {
    return this.http.get<CycleCandidate[]>(`${this.url(cycleId)}/addable-employees`, { params: query ? { query } : {} });
  }

  /** `POST /{id}/reviews` — adds people to the cycle; returns their new reviews. */
  addEmployees(cycleId: string, employeeIds: string[]): Observable<PerformanceReview[]> {
    return this.http.post<PerformanceReview[]>(`${this.url(cycleId)}/reviews`, { employeeIds });
  }

  private url(cycleId: string): string {
    return `${this.resourceUrl}/${encodeURIComponent(cycleId)}`;
  }
}
