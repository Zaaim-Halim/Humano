import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeSignatureRequest, EmployeeSignature, UpdateEmployeeSignatureRequest } from '../models/employee-signature.model';

/** EmployeeSignature — `/api/hr/employee-signatures`. */
@Injectable({ providedIn: 'root' })
export class EmployeeSignatureService extends RestResourceService<
  EmployeeSignature,
  EmployeeSignature,
  CreateEmployeeSignatureRequest,
  UpdateEmployeeSignatureRequest
> {
  constructor() {
    super('api/hr/employee-signatures');
  }

  /** `GET /api/hr/employee-signatures/employee/{employeeId}` — record for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeSignature> {
    return this.http.get<EmployeeSignature>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
