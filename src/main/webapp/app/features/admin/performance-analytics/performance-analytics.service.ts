import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { ReviewCycleType } from 'app/features/employee';

import { PerformanceAnalytics } from './performance-analytics.model';

/** Performance analytics — `/api/hr/performance-analytics`, for `VIEW_PERFORMANCE_REVIEWS`. */
@Injectable({ providedIn: 'root' })
export class PerformanceAnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/performance-analytics');

  /** The latest `limit` cycles of `type`, newest first. */
  analytics(type: ReviewCycleType, limit: number): Observable<PerformanceAnalytics> {
    return this.http.get<PerformanceAnalytics>(this.resourceUrl, { params: new HttpParams().set('type', type).set('limit', limit) });
  }
}
