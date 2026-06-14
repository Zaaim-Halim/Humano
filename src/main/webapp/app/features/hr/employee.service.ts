import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import {
  CreateEmployeeProfileRequest,
  EmployeeProfile,
  EmployeeSearchRequest,
  SimpleEmployeeProfile,
  UpdateEmployeeProfileRequest,
} from './employee.model';

/**
 * Employees — `/api/hr/employees`. List/detail use distinct shapes
 * (`SimpleEmployeeProfile` vs `EmployeeProfile`); creation is keyed by an
 * existing user id, so the inherited `POST /` create is disabled in favour of
 * `createForUser`.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeService extends RestResourceService<
  SimpleEmployeeProfile,
  EmployeeProfile,
  CreateEmployeeProfileRequest,
  UpdateEmployeeProfileRequest
> {
  constructor() {
    super('api/hr/employees');
  }

  /** Create a profile for an existing user — `POST /api/hr/employees/{userId}`. */
  createForUser(userId: string, body: CreateEmployeeProfileRequest): Observable<EmployeeProfile> {
    return this.http.post<EmployeeProfile>(`${this.resourceUrl}/${encodeURIComponent(userId)}`, body);
  }

  /** `POST /api/hr/employees/search` — criteria in body, pagination in query. */
  search(criteria: EmployeeSearchRequest, req?: PageRequest): Observable<Page<SimpleEmployeeProfile>> {
    return this.http.post<Page<SimpleEmployeeProfile>>(`${this.resourceUrl}/search`, criteria, { params: createRequestOption(req) });
  }

  /** Employees are created via {@link createForUser}; the unscoped create is unsupported. */
  override create(): never {
    throw new Error('Use createForUser(userId, body): employees are created at POST /api/hr/employees/{userId}.');
  }
}
