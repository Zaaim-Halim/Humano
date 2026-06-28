import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeLicenseRequest, EmployeeLicense, UpdateEmployeeLicenseRequest } from '../models/employee-license.model';

/** EmployeeLicense — `/api/hr/employee-licenses`. */
@Injectable({ providedIn: 'root' })
export class EmployeeLicenseService extends RestResourceService<
  EmployeeLicense,
  EmployeeLicense,
  CreateEmployeeLicenseRequest,
  UpdateEmployeeLicenseRequest
> {
  constructor() {
    super('api/hr/employee-licenses');
  }

  /** `GET /api/hr/employee-licenses/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeLicense[]> {
    return this.http.get<EmployeeLicense[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
