import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateOrganizationalUnitRequest, OrganizationalUnit, UpdateOrganizationalUnitRequest } from '../models/organizational-unit.model';

/** Organizational units — `/api/hr/organizational-units` (CRUD + tree traversal). */
@Injectable({ providedIn: 'root' })
export class OrganizationalUnitService extends RestResourceService<
  OrganizationalUnit,
  OrganizationalUnit,
  CreateOrganizationalUnitRequest,
  UpdateOrganizationalUnitRequest
> {
  constructor() {
    super('api/hr/organizational-units');
  }

  /** `GET /api/hr/organizational-units/roots` — top-level units. */
  roots(req?: PageRequest): Observable<Page<OrganizationalUnit>> {
    return this.http.get<Page<OrganizationalUnit>>(`${this.resourceUrl}/roots`, { params: createRequestOption(req) });
  }

  /** `GET /api/hr/organizational-units/{parentId}/sub-units` — direct children. */
  subUnits(parentId: string, req?: PageRequest): Observable<Page<OrganizationalUnit>> {
    return this.http.get<Page<OrganizationalUnit>>(`${this.resourceUrl}/${encodeURIComponent(parentId)}/sub-units`, {
      params: createRequestOption(req),
    });
  }
}
