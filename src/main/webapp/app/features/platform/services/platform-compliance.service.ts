import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { NewSubprocessorNotice, ScheduledDeletions, SubprocessorNotice } from '../models/compliance.model';

/** Owner console — data protection (`/api/platform/compliance`): scheduled deletions and sub-processor notices. */
@Injectable({ providedIn: 'root' })
export class PlatformComplianceService {
  private readonly http = inject(HttpClient);
  private readonly url = inject(ApplicationConfigService).getEndpointFor('api/platform/compliance');

  deletions(): Observable<ScheduledDeletions> {
    return this.http.get<ScheduledDeletions>(`${this.url}/deletions`);
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
