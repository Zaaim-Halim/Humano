import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { ReviewRetention, ReviewRetentionPurgeResult, ReviewRetentionSettings } from './review-retention.model';

/** Retention of performance review records — `/api/hr/workflows/review-retention`, for `CONFIGURE_PERFORMANCE_CYCLES`. */
@Injectable({ providedIn: 'root' })
export class ReviewRetentionService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/review-retention');

  get(): Observable<ReviewRetention> {
    return this.http.get<ReviewRetention>(this.resourceUrl);
  }

  /** Saves the settings; deletes nothing. */
  save(body: ReviewRetentionSettings): Observable<ReviewRetention> {
    return this.http.put<ReviewRetention>(this.resourceUrl, body);
  }

  /** Deletes exactly these cycles — the server refuses if they are no longer the ones due. */
  purge(cycleIds: string[]): Observable<ReviewRetentionPurgeResult> {
    return this.http.post<ReviewRetentionPurgeResult>(`${this.resourceUrl}/purge`, { cycleIds });
  }
}
