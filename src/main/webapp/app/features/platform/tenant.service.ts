import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { TenantStatus } from './enums/tenant-status.enum';
import { Tenant, TenantDetail } from './tenant.model';

/**
 * Platform tenants — `/api/platform/tenants` (superadmin). List is status-
 * filterable; detail returns tenant + live DB pool stats; lifecycle is
 * suspend/activate/deprovision rather than plain update.
 */
@Injectable({ providedIn: 'root' })
export class TenantService extends RestResourceService<Tenant, TenantDetail> {
  constructor() {
    super('api/platform/tenants');
  }

  /** `GET /api/platform/tenants?status=` — optionally filter by status. */
  list(status?: TenantStatus, req?: PageRequest): Observable<Page<Tenant>> {
    return this.http.get<Page<Tenant>>(this.resourceUrl, { params: createRequestOption({ status, ...req }) });
  }

  /** `POST /api/platform/tenants/{id}/suspend`. */
  suspend(id: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(id)}/suspend`, {});
  }

  /** `POST /api/platform/tenants/{id}/activate`. */
  activate(id: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(id)}/activate`, {});
  }

  /** `DELETE /api/platform/tenants/{id}` — deprovision (destructive). */
  deprovision(id: string): Observable<void> {
    return this.delete(id);
  }

  /** Tenants are provisioned via the onboarding flow (`TenantRegistrationDTO`), not a plain create. */
  override create(): never {
    throw new Error('Tenant provisioning uses the onboarding flow (POST /api/platform/tenants with TenantRegistrationDTO), not create().');
  }

  /** No tenant-edit endpoint exists on the platform resource; use the lifecycle actions. */
  override update(): never {
    throw new Error('No PUT /api/platform/tenants/{id}: use suspend/activate/deprovision, or /api/tenant/me for self-edit.');
  }
}
