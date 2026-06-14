import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { CreatePositionRequest, Position, UpdatePositionRequest } from './position.model';

/** Positions — `/api/hr/positions` (standard CRUD + by-unit listing). */
@Injectable({ providedIn: 'root' })
export class PositionService extends RestResourceService<Position, Position, CreatePositionRequest, UpdatePositionRequest> {
  constructor() {
    super('api/hr/positions');
  }

  /** `GET /api/hr/positions/unit/{unitId}` — positions within an org unit. */
  byUnit(unitId: string, req?: PageRequest): Observable<Page<Position>> {
    return this.http.get<Page<Position>>(`${this.resourceUrl}/unit/${encodeURIComponent(unitId)}`, { params: createRequestOption(req) });
  }
}
