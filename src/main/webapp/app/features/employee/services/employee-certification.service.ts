import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import {
  CreateEmployeeCertificationRequest,
  EmployeeCertification,
  UpdateEmployeeCertificationRequest,
} from '../models/employee-certification.model';

/** EmployeeCertification — `/api/hr/employee-certifications`. */
@Injectable({ providedIn: 'root' })
export class EmployeeCertificationService extends RestResourceService<
  EmployeeCertification,
  EmployeeCertification,
  CreateEmployeeCertificationRequest,
  UpdateEmployeeCertificationRequest
> {
  constructor() {
    super('api/hr/employee-certifications');
  }

  /** `GET /api/hr/employee-certifications/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeCertification[]> {
    return this.http.get<EmployeeCertification[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
