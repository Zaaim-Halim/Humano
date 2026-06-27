import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

/**
 * Public (unauthenticated) account flows — activate, password reset.
 * There is no self-registration: accounts are provisioned by HR via the
 * employee create form, and recipients set their password through reset.
 */
@Injectable({ providedIn: 'root' })
export class AuthPublicService {
  private readonly http = inject(HttpClient);
  private readonly appConfig = inject(ApplicationConfigService);

  /** `GET /api/activate?key=`. */
  activate(key: string): Observable<void> {
    return this.http.get<void>(this.appConfig.getEndpointFor('api/activate'), { params: new HttpParams().set('key', key) });
  }

  /** `POST /api/account/reset-password/init` — `RequestPasswordResetRequest` ({ email }). */
  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(this.appConfig.getEndpointFor('api/account/reset-password/init'), { email });
  }

  /** `POST /api/account/reset-password/finish`. */
  finishPasswordReset(key: string, newPassword: string): Observable<void> {
    return this.http.post<void>(this.appConfig.getEndpointFor('api/account/reset-password/finish'), { key, newPassword });
  }
}
