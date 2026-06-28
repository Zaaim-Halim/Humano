import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateWorkPermitRequest, WorkPermit, UpdateWorkPermitRequest } from '../models/work-permit.model';

/** WorkPermit — `/api/hr/work-permits`. */
@Injectable({ providedIn: 'root' })
export class WorkPermitService extends RestResourceService<WorkPermit, WorkPermit, CreateWorkPermitRequest, UpdateWorkPermitRequest> {
  constructor() {
    super('api/hr/work-permits');
  }

  /** `GET /api/hr/work-permits/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<WorkPermit[]> {
    return this.http.get<WorkPermit[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
