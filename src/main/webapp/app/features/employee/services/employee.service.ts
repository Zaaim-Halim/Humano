import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import {
  CreateEmployeeRequest,
  EmployeeProfile,
  EmployeeSearchRequest,
  SimpleEmployeeProfile,
  UpdateEmployeeProfileRequest,
} from '../models/employee.model';

/**
 * Employees — `/api/hr/employees`. List/detail use distinct shapes
 * (`SimpleEmployeeProfile` vs `EmployeeProfile`). Creation provisions the
 * backing user account and the HR profile in one step (`POST /`), so the
 * inherited `create` is used directly — there is no separate user management.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeService extends RestResourceService<
  SimpleEmployeeProfile,
  EmployeeProfile,
  CreateEmployeeRequest,
  UpdateEmployeeProfileRequest
> {
  constructor() {
    super('api/hr/employees');
  }

  /** `POST /api/hr/employees/search` — criteria in body, pagination in query. */
  search(criteria: EmployeeSearchRequest, req?: PageRequest): Observable<Page<SimpleEmployeeProfile>> {
    return this.http.post<Page<SimpleEmployeeProfile>>(`${this.resourceUrl}/search`, criteria, { params: createRequestOption(req) });
  }

  /** `GET /api/hr/employees/assignable-roles` — role names grantable on the form. */
  getAssignableRoles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.resourceUrl}/assignable-roles`);
  }
}
