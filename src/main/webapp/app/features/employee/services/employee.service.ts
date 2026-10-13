import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import {
  CreateEmployeeRequest,
  EmployeeProfile,
  EmployeeDirectoryStats,
  EmployeeSearchRequest,
  SimpleEmployeeProfile,
  UpdateEmployeeProfileRequest,
  personName,
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

  /**
   * Options for an employee picker (autocomplete): matches name, title, email or employee
   * number; labelled by name with the job title underneath.
   */
  searchOptions(term: string, size = 10): Observable<{ value: string; label: string; sublabel?: string | null }[]> {
    return this.search({ query: term }, { page: 0, size }).pipe(
      map(res => res.content.map(e => ({ value: e.id, label: personName(e), sublabel: e.jobTitle ?? e.departmentName }))),
    );
  }

  /** `GET /api/hr/employees/stats` — headcount by status, department and work location. */
  stats(): Observable<EmployeeDirectoryStats> {
    return this.http.get<EmployeeDirectoryStats>(`${this.resourceUrl}/stats`);
  }

  /** `GET /api/hr/employees/assignable-roles` — role names grantable on the form. */
  getAssignableRoles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.resourceUrl}/assignable-roles`);
  }
}
