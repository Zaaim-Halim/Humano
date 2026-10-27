import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { ReviewCalendar, UpdateReviewCalendarRequest } from '../models/review-cycle.model';

/**
 * The company's review calendar — `/api/hr/workflows/review-calendar`: when review periods run,
 * how long each phase lasts, and the next cycle it proposes. Read with `VIEW_PERFORMANCE_REVIEWS`
 * or `CONFIGURE_PERFORMANCE_CYCLES`; saved with `CONFIGURE_PERFORMANCE_CYCLES`.
 */
@Injectable({ providedIn: 'root' })
export class ReviewCalendarService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/review-calendar');

  get(): Observable<ReviewCalendar> {
    return this.http.get<ReviewCalendar>(this.resourceUrl);
  }

  save(body: UpdateReviewCalendarRequest): Observable<ReviewCalendar> {
    return this.http.put<ReviewCalendar>(this.resourceUrl, body);
  }
}
