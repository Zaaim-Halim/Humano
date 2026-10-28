import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import {
  NewSubprocessorNotice,
  RetentionChange,
  RetentionSettings,
  ScheduledDeletions,
  SubprocessorNotice,
} from '../models/compliance.model';

/** Owner console — data protection (`/api/platform/compliance`): retention period, scheduled deletions, sub-processor notices. */
@Injectable({ providedIn: 'root' })
export class PlatformComplianceService {
  private readonly http = inject(HttpClient);
  private readonly url = inject(ApplicationConfigService).getEndpointFor('api/platform/compliance');

  deletions(): Observable<ScheduledDeletions> {
    return this.http.get<ScheduledDeletions>(`${this.url}/deletions`);
  }

  retention(): Observable<RetentionSettings> {
    return this.http.get<RetentionSettings>(`${this.url}/retention`);
  }

  /** Sets the retention period (`MANAGE_PLATFORM_BILLING`): longer at once, shorter from a date far enough ahead. */
  changeRetention(body: RetentionChange): Observable<RetentionSettings> {
    return this.http.put<RetentionSettings>(`${this.url}/retention`, body);
  }

  /** Cancels a scheduled change of the retention period. */
  cancelScheduledRetention(): Observable<RetentionSettings> {
    return this.http.delete<RetentionSettings>(`${this.url}/retention/scheduled`);
  }

  notices(): Observable<SubprocessorNotice[]> {
    return this.http.get<SubprocessorNotice[]>(`${this.url}/subprocessor-notices`);
  }

  /** How many workspaces a notice would reach now. */
  recipientCount(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.url}/subprocessor-notices/recipients`).pipe(map(r => r.count));
  }

  /** Emails the notice to every customer whose data we hold. */
  sendNotice(body: NewSubprocessorNotice): Observable<SubprocessorNotice> {
    return this.http.post<SubprocessorNotice>(`${this.url}/subprocessor-notices`, body);
  }
}
