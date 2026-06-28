import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeEducationRequest, EmployeeEducation, UpdateEmployeeEducationRequest } from '../models/employee-education.model';

/** EmployeeEducation — `/api/hr/employee-educations`. */
@Injectable({ providedIn: 'root' })
export class EmployeeEducationService extends RestResourceService<
  EmployeeEducation,
  EmployeeEducation,
  CreateEmployeeEducationRequest,
  UpdateEmployeeEducationRequest
> {
  constructor() {
    super('api/hr/employee-educations');
  }

  /** `GET /api/hr/employee-educations/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeEducation[]> {
    return this.http.get<EmployeeEducation[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
