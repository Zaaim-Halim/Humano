import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

/** `POST /api/register` body. */
export interface RegisterUserRequest {
  login: string;
  firstName?: string;
  lastName?: string;
  email: string;
  password: string;
  langKey: string;
}

/** Public (unauthenticated) account flows — register, activate, password reset. */
@Injectable({ providedIn: 'root' })
export class AuthPublicService {
  private readonly http = inject(HttpClient);
  private readonly appConfig = inject(ApplicationConfigService);

  /** `POST /api/register`. */
  register(body: RegisterUserRequest): Observable<void> {
    return this.http.post<void>(this.appConfig.getEndpointFor('api/register'), body);
  }

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
