import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

/** A persistent login session (`PersistentToken`) — `GET /api/account/sessions`. */
export interface Session {
  series: string;
  /** ISO date `YYYY-MM-DD`. */
  tokenDate: string;
  ipAddress: string;
  userAgent: string;
}

/** Password + session management against the self-service `/api/account/*` endpoints. */
@Injectable({ providedIn: 'root' })
export class AccountManagementService {
  private readonly http = inject(HttpClient);
  private readonly appConfig = inject(ApplicationConfigService);

  /** `POST /api/account/change-password`. */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(this.appConfig.getEndpointFor('api/account/change-password'), { currentPassword, newPassword });
  }

  /** `GET /api/account/sessions`. */
  sessions(): Observable<Session[]> {
    return this.http.get<Session[]>(this.appConfig.getEndpointFor('api/account/sessions'));
  }

  /** `DELETE /api/account/sessions/{series}` — revoke a session. */
  invalidate(series: string): Observable<void> {
    return this.http.delete<void>(this.appConfig.getEndpointFor(`api/account/sessions/${encodeURIComponent(series)}`));
  }
}
