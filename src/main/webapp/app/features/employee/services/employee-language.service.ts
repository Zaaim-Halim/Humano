import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeLanguageRequest, EmployeeLanguage, UpdateEmployeeLanguageRequest } from '../models/employee-language.model';

/** EmployeeLanguage — `/api/hr/employee-languages`. */
@Injectable({ providedIn: 'root' })
export class EmployeeLanguageService extends RestResourceService<
  EmployeeLanguage,
  EmployeeLanguage,
  CreateEmployeeLanguageRequest,
  UpdateEmployeeLanguageRequest
> {
  constructor() {
    super('api/hr/employee-languages');
  }

  /** `GET /api/hr/employee-languages/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeLanguage[]> {
    return this.http.get<EmployeeLanguage[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
