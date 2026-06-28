import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import {
  CreateEmployeeBankAccountRequest,
  EmployeeBankAccount,
  UpdateEmployeeBankAccountRequest,
} from '../models/employee-bank-account.model';

/** EmployeeBankAccount — `/api/hr/employee-bank-accounts`. */
@Injectable({ providedIn: 'root' })
export class EmployeeBankAccountService extends RestResourceService<
  EmployeeBankAccount,
  EmployeeBankAccount,
  CreateEmployeeBankAccountRequest,
  UpdateEmployeeBankAccountRequest
> {
  constructor() {
    super('api/hr/employee-bank-accounts');
  }

  /** `GET /api/hr/employee-bank-accounts/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeBankAccount[]> {
    return this.http.get<EmployeeBankAccount[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
