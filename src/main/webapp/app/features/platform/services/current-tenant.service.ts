import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { Tenant } from '../models/tenant.model';

import { UpdateCurrentTenantRequest } from '../models/current-tenant.model';

/**
 * Current tenant self-context — `/api/tenant` (tenant-scoped, not superadmin).
 * `me` returns the active tenant for the shell's tenant switcher. Returns the
 * same `Tenant` shape (`TenantResponse`) as the platform list.
 */
@Injectable({ providedIn: 'root' })
export class CurrentTenantService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/tenant');

  /** `GET /api/tenant/me` — the active tenant context. */
  current(): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.resourceUrl}/me`);
  }

  /** `PUT /api/tenant/me` — self-service tenant edit. */
  updateCurrent(body: UpdateCurrentTenantRequest): Observable<Tenant> {
    return this.http.put<Tenant>(`${this.resourceUrl}/me`, body);
  }

  // Organizations (`/organizations`) and storage-configs (`/storage-configs`)
  // also live under /api/tenant — add when a settings surface needs them.
}
