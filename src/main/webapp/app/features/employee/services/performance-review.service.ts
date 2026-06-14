import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import {
  CreatePerformanceReviewRequest,
  PerformanceReview,
  PerformanceReviewSearchRequest,
  UpdatePerformanceReviewRequest,
} from '../models/performance-review.model';

/** Performance reviews — `/api/hr/performance-reviews` (standard CRUD + search). */
@Injectable({ providedIn: 'root' })
export class PerformanceReviewService extends RestResourceService<
  PerformanceReview,
  PerformanceReview,
  CreatePerformanceReviewRequest,
  UpdatePerformanceReviewRequest
> {
  constructor() {
    super('api/hr/performance-reviews');
  }

  /** `GET /api/hr/performance-reviews/search` — criteria + pagination as query params. */
  search(criteria: PerformanceReviewSearchRequest, req?: PageRequest): Observable<Page<PerformanceReview>> {
    return this.http.get<Page<PerformanceReview>>(`${this.resourceUrl}/search`, { params: createRequestOption({ ...criteria, ...req }) });
  }
}
