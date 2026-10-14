import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { AdminUser, RolePermissions, UpdateAdminUserRequest } from '../models/user-admin.model';

/** User accounts and roles — `/api/admin/users` and `/api/authorities` (admin only). */
@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);

  /**
   * One page of user accounts with their roles, searched (login, email, name) and filtered by role
   * on the server; `total` comes from the `X-Total-Count` header.
   */
  page(params: { page: number; size: number; q?: string; role?: string }): Observable<{ users: AdminUser[]; total: number }> {
    return this.http
      .get<AdminUser[]>(this.config.getEndpointFor('api/admin/users'), {
        params: createRequestOption({
          page: params.page,
          size: params.size,
          sort: ['firstName,asc', 'lastName,asc', 'login,asc'],
          ...(params.q ? { q: params.q } : {}),
          ...(params.role ? { role: params.role } : {}),
        }),
        observe: 'response',
      })
      .pipe(map(res => ({ users: res.body ?? [], total: Number(res.headers.get('X-Total-Count') ?? res.body?.length ?? 0) })));
  }

  update(body: UpdateAdminUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(this.config.getEndpointFor('api/admin/users'), body);
  }

  /** Every role with the permissions it grants. */
  roles(): Observable<RolePermissions[]> {
    return this.http.get<RolePermissions[]>(this.config.getEndpointFor('api/authorities/overview'));
  }
}
