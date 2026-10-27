import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import {
  CompensationProposal,
  CompensationReview,
  CompensationReviewCycle,
  MeritGuidelines,
  SaveCompensationProposalRequest,
} from './compensation-review.model';

/**
 * Compensation review — `/api/hr/compensation-reviews`. Drafted, approved by someone else and applied
 * by holders of `MANAGE_COMPENSATION`; applying a bonus also needs `MANAGE_BONUSES`.
 */
@Injectable({ providedIn: 'root' })
export class CompensationReviewService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/compensation-reviews');

  guidelines(): Observable<MeritGuidelines> {
    return this.http.get<MeritGuidelines>(`${this.resourceUrl}/guidelines`);
  }

  saveGuidelines(body: MeritGuidelines): Observable<MeritGuidelines> {
    return this.http.put<MeritGuidelines>(`${this.resourceUrl}/guidelines`, body);
  }

  /** The regular review cycles, latest first. */
  cycles(): Observable<CompensationReviewCycle[]> {
    return this.http.get<CompensationReviewCycle[]>(`${this.resourceUrl}/cycles`);
  }

  review(cycleId: string): Observable<CompensationReview> {
    return this.http.get<CompensationReview>(`${this.resourceUrl}/cycles/${cycleId}`);
  }

  /** Drafts a proposal for each eligible review; emits how many. */
  generate(cycleId: string, effectiveDate: string): Observable<number> {
    return this.count(`${this.resourceUrl}/cycles/${cycleId}/proposals`, { effectiveDate });
  }

  /** Fills drafts without a new salary with the merit matrix's midpoint; emits how many. */
  useGuidance(cycleId: string): Observable<number> {
    return this.count(`${this.resourceUrl}/cycles/${cycleId}/guidance`);
  }

  submitAll(cycleId: string): Observable<number> {
    return this.count(`${this.resourceUrl}/cycles/${cycleId}/submit-all`);
  }

  approveAll(cycleId: string): Observable<number> {
    return this.count(`${this.resourceUrl}/cycles/${cycleId}/approve-all`);
  }

  update(id: string, body: SaveCompensationProposalRequest): Observable<CompensationProposal> {
    return this.http.put<CompensationProposal>(`${this.resourceUrl}/proposals/${id}`, body);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/proposals/${id}`);
  }

  submit(id: string): Observable<CompensationProposal> {
    return this.http.post<CompensationProposal>(`${this.resourceUrl}/proposals/${id}/submit`, {});
  }

  approve(id: string): Observable<CompensationProposal> {
    return this.http.post<CompensationProposal>(`${this.resourceUrl}/proposals/${id}/approve`, {});
  }

  returnToDraft(id: string, comment: string): Observable<CompensationProposal> {
    return this.http.post<CompensationProposal>(`${this.resourceUrl}/proposals/${id}/return`, { comment });
  }

  apply(id: string): Observable<CompensationProposal> {
    return this.http.post<CompensationProposal>(`${this.resourceUrl}/proposals/${id}/apply`, {});
  }

  private count(url: string, body: object = {}): Observable<number> {
    return this.http.post<{ count: number }>(url, body).pipe(map(r => r.count));
  }
}
