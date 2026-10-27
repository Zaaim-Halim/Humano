import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import {
  ImprovementPlan,
  ImprovementPlanOutcome,
  ImprovementPlanProgress,
  ReviewImprovementPlanStatus,
  SaveImprovementPlanRequest,
} from '../models/improvement-plan.model';

/**
 * Performance improvement plans — `/api/hr/improvement-plans`. The employee's manager (or whoever runs
 * the plan, or HR with `MANAGE_PERFORMANCE_REVIEWS`) runs it; the employee reads and acknowledges it.
 */
@Injectable({ providedIn: 'root' })
export class ImprovementPlanService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/improvement-plans');

  /** The signed-in employee's plans, once shared with them. */
  mine(): Observable<ImprovementPlan[]> {
    return this.http.get<ImprovementPlan[]>(`${this.resourceUrl}/mine`);
  }

  /** The plans the signed-in user runs (every plan for HR). */
  managed(): Observable<ImprovementPlan[]> {
    return this.http.get<ImprovementPlan[]>(this.resourceUrl);
  }

  find(id: string): Observable<ImprovementPlan> {
    return this.http.get<ImprovementPlan>(`${this.resourceUrl}/${id}`);
  }

  forReview(reviewId: string): Observable<ReviewImprovementPlanStatus> {
    return this.http.get<ReviewImprovementPlanStatus>(`${this.resourceUrl}/reviews/${reviewId}`);
  }

  start(reviewId: string, body: SaveImprovementPlanRequest): Observable<ImprovementPlan> {
    return this.http.post<ImprovementPlan>(`${this.resourceUrl}/reviews/${reviewId}`, body);
  }

  update(id: string, body: SaveImprovementPlanRequest): Observable<ImprovementPlan> {
    return this.http.put<ImprovementPlan>(`${this.resourceUrl}/${id}`, body);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${id}`);
  }

  activate(id: string): Observable<ImprovementPlan> {
    return this.http.post<ImprovementPlan>(`${this.resourceUrl}/${id}/activate`, {});
  }

  acknowledge(id: string, comment: string | null): Observable<ImprovementPlan> {
    return this.http.post<ImprovementPlan>(`${this.resourceUrl}/${id}/acknowledgement`, { comment });
  }

  checkIn(id: string, progress: ImprovementPlanProgress, note: string): Observable<ImprovementPlan> {
    return this.http.post<ImprovementPlan>(`${this.resourceUrl}/${id}/check-ins`, { progress, note });
  }

  extend(id: string, endDate: string, reason: string): Observable<ImprovementPlan> {
    return this.http.post<ImprovementPlan>(`${this.resourceUrl}/${id}/extension`, { endDate, reason });
  }

  close(id: string, outcome: ImprovementPlanOutcome, note: string): Observable<ImprovementPlan> {
    return this.http.post<ImprovementPlan>(`${this.resourceUrl}/${id}/close`, { outcome, note });
  }
}
