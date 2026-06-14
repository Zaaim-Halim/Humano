import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateUserRequest, ManagedUser, UpdateUserRequest } from '../models/managed-user.model';

/** One page of admin users (this endpoint returns a `List` body + `X-Total-Count` header). */
export interface ManagedUserPage {
  content: ManagedUser[];
  total: number;
}

/** Admin user management — `/api/admin/users` (ADMIN). */
@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/admin/users');

  /** `GET /api/admin/users` — header-paginated; reads `X-Total-Count`. */
  query(req?: PageRequest): Observable<ManagedUserPage> {
    return this.http
      .get<ManagedUser[]>(this.resourceUrl, { params: createRequestOption(req), observe: 'response' })
      .pipe(map(res => ({ content: res.body ?? [], total: Number(res.headers.get('X-Total-Count') ?? res.body?.length ?? 0) })));
  }

  /** `GET /api/admin/users/{login}`. */
  find(login: string): Observable<ManagedUser> {
    return this.http.get<ManagedUser>(`${this.resourceUrl}/${encodeURIComponent(login)}`);
  }

  /** `POST /api/admin/users`. */
  create(body: CreateUserRequest): Observable<ManagedUser> {
    return this.http.post<ManagedUser>(this.resourceUrl, body);
  }

  /** `PUT /api/admin/users`. */
  update(body: UpdateUserRequest): Observable<ManagedUser> {
    return this.http.put<ManagedUser>(this.resourceUrl, body);
  }

  /** `DELETE /api/admin/users/{login}`. */
  delete(login: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${encodeURIComponent(login)}`);
  }

  /** `GET /api/admin/users/authorities` — assignable role names. */
  authorities(): Observable<string[]> {
    return this.http.get<string[]>(`${this.resourceUrl}/authorities`);
  }
}
