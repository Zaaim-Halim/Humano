import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmploymentContractRequest, EmploymentContract, UpdateEmploymentContractRequest } from '../models/employment-contract.model';

/** EmploymentContract — `/api/hr/employment-contracts`. */
@Injectable({ providedIn: 'root' })
export class EmploymentContractService extends RestResourceService<
  EmploymentContract,
  EmploymentContract,
  CreateEmploymentContractRequest,
  UpdateEmploymentContractRequest
> {
  constructor() {
    super('api/hr/employment-contracts');
  }

  /** `GET /api/hr/employment-contracts/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmploymentContract[]> {
    return this.http.get<EmploymentContract[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
