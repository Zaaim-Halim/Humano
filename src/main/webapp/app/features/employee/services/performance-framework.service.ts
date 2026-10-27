import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Competency, ReviewTemplate, SaveCompetencyRequest, SaveReviewTemplateRequest } from '../models/performance-framework.model';

/**
 * The performance framework — `/api/hr/performance-frameworks`. Read by anyone who sees or sets up
 * review cycles; changed with `MANAGE_PERFORMANCE_FRAMEWORKS`.
 */
@Injectable({ providedIn: 'root' })
export class PerformanceFrameworkService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/performance-frameworks');

  competencies(): Observable<Competency[]> {
    return this.http.get<Competency[]>(`${this.resourceUrl}/competencies`);
  }

  createCompetency(body: SaveCompetencyRequest): Observable<Competency> {
    return this.http.post<Competency>(`${this.resourceUrl}/competencies`, body);
  }

  updateCompetency(id: string, body: SaveCompetencyRequest): Observable<Competency> {
    return this.http.put<Competency>(`${this.resourceUrl}/competencies/${id}`, body);
  }

  deleteCompetency(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/competencies/${id}`);
  }

  templates(): Observable<ReviewTemplate[]> {
    return this.http.get<ReviewTemplate[]>(`${this.resourceUrl}/templates`);
  }

  createTemplate(body: SaveReviewTemplateRequest): Observable<ReviewTemplate> {
    return this.http.post<ReviewTemplate>(`${this.resourceUrl}/templates`, body);
  }

  updateTemplate(id: string, body: SaveReviewTemplateRequest): Observable<ReviewTemplate> {
    return this.http.put<ReviewTemplate>(`${this.resourceUrl}/templates/${id}`, body);
  }

  deleteTemplate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/templates/${id}`);
  }
}
