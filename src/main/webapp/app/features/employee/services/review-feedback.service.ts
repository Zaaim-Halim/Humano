import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { AutocompleteOption } from 'app/shared/ui';

import {
  FeedbackCandidate,
  RequestFeedbackRequest,
  ReviewFeedback,
  ReviewFeedbackCount,
  ReviewFeedbackOverview,
  SubmitFeedbackRequest,
} from '../models/review-feedback.model';

/**
 * Additional feedback on cycle reviews — under `/api/hr/workflows/review-cycles`. Checked per
 * request: the reviewer (or HR with `MANAGE_PERFORMANCE_REVIEWS`) asks, approves and cancels; the
 * reviewed employee nominates and withdraws their nominations; the contributor answers or declines.
 */
@Injectable({ providedIn: 'root' })
export class ReviewFeedbackService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/review-cycles');

  /** A review's requests, as the caller may see them. */
  forReview(cycleId: string, reviewId: string): Observable<ReviewFeedbackOverview> {
    return this.http.get<ReviewFeedbackOverview>(this.reviewUrl(cycleId, reviewId));
  }

  /** Ask someone (reviewer, HR) or nominate them (the reviewed employee). */
  request(cycleId: string, reviewId: string, body: RequestFeedbackRequest): Observable<ReviewFeedback> {
    return this.http.post<ReviewFeedback>(this.reviewUrl(cycleId, reviewId), body);
  }

  /** Colleagues who could be asked, by name (at most 10) — for the reviewer, HR and the reviewed employee. */
  candidates(cycleId: string, reviewId: string, query: string): Observable<AutocompleteOption[]> {
    return this.http
      .get<FeedbackCandidate[]>(
        `${this.resourceUrl}/${encodeURIComponent(cycleId)}/reviews/${encodeURIComponent(reviewId)}/feedback-candidates`,
        {
          params: { query },
        },
      )
      .pipe(map(list => list.map(c => ({ value: c.id, label: c.name, sublabel: c.jobTitle ?? c.departmentName }))));
  }

  /** Requested and received per review of a cycle (HR). */
  counts(cycleId: string): Observable<ReviewFeedbackCount[]> {
    return this.http.get<ReviewFeedbackCount[]>(`${this.resourceUrl}/${encodeURIComponent(cycleId)}/feedback-counts`);
  }

  /** Feedback the signed-in employee was asked for, in running cycles. */
  mine(): Observable<ReviewFeedback[]> {
    return this.http.get<ReviewFeedback[]>(`${this.resourceUrl}/my-feedback-requests`);
  }

  /** One request, for the contributor answering it. */
  find(requestId: string): Observable<ReviewFeedback> {
    return this.http.get<ReviewFeedback>(this.requestUrl(requestId));
  }

  submit(requestId: string, body: SubmitFeedbackRequest): Observable<ReviewFeedback> {
    return this.http.put<ReviewFeedback>(`${this.requestUrl(requestId)}/submit`, body);
  }

  approve(requestId: string): Observable<ReviewFeedback> {
    return this.http.post<ReviewFeedback>(`${this.requestUrl(requestId)}/approve`, null);
  }

  /** The contributor declines (a reason is required), or the reviewer turns a nomination down. */
  decline(requestId: string, reason?: string | null): Observable<ReviewFeedback> {
    return this.http.post<ReviewFeedback>(`${this.requestUrl(requestId)}/decline`, reason ? { reason } : {});
  }

  cancel(requestId: string): Observable<ReviewFeedback> {
    return this.http.post<ReviewFeedback>(`${this.requestUrl(requestId)}/cancel`, null);
  }

  private reviewUrl(cycleId: string, reviewId: string): string {
    return `${this.resourceUrl}/${encodeURIComponent(cycleId)}/reviews/${encodeURIComponent(reviewId)}/feedback-requests`;
  }

  private requestUrl(requestId: string): string {
    return `${this.resourceUrl}/feedback-requests/${encodeURIComponent(requestId)}`;
  }
}
