import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import {
  CreateEmployeeMedicalProfileRequest,
  EmployeeMedicalProfile,
  UpdateEmployeeMedicalProfileRequest,
} from '../models/employee-medical-profile.model';

/** EmployeeMedicalProfile — `/api/hr/employee-medical-profiles`. */
@Injectable({ providedIn: 'root' })
export class EmployeeMedicalProfileService extends RestResourceService<
  EmployeeMedicalProfile,
  EmployeeMedicalProfile,
  CreateEmployeeMedicalProfileRequest,
  UpdateEmployeeMedicalProfileRequest
> {
  constructor() {
    super('api/hr/employee-medical-profiles');
  }

  /** `GET /api/hr/employee-medical-profiles/employee/{employeeId}` — record for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeMedicalProfile> {
    return this.http.get<EmployeeMedicalProfile>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
