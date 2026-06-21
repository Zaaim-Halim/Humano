import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { OrganizationSettings, UpdateOrganizationSettingsRequest } from '../models/org-settings.model';

/** Company pay-policy — `/api/org-settings` (singleton; gated `CONFIGURE_TENANT_SETTINGS`). */
@Injectable({ providedIn: 'root' })
export class OrganizationSettingsService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/org-settings');

  /** `GET /api/org-settings` — current settings, or server-supplied defaults (id null). */
  get(): Observable<OrganizationSettings> {
    return this.http.get<OrganizationSettings>(this.resourceUrl);
  }

  /** `PUT /api/org-settings` — replace the settings. */
  update(body: UpdateOrganizationSettingsRequest): Observable<OrganizationSettings> {
    return this.http.put<OrganizationSettings>(this.resourceUrl, body);
  }
}
