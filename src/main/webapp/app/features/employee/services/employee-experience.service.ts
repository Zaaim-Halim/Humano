import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeExperienceRequest, EmployeeExperience, UpdateEmployeeExperienceRequest } from '../models/employee-experience.model';

/** EmployeeExperience — `/api/hr/employee-experiences`. */
@Injectable({ providedIn: 'root' })
export class EmployeeExperienceService extends RestResourceService<
  EmployeeExperience,
  EmployeeExperience,
  CreateEmployeeExperienceRequest,
  UpdateEmployeeExperienceRequest
> {
  constructor() {
    super('api/hr/employee-experiences');
  }

  /** `GET /api/hr/employee-experiences/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeExperience[]> {
    return this.http.get<EmployeeExperience[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
