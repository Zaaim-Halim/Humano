import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeAssetRequest, EmployeeAsset, UpdateEmployeeAssetRequest } from '../models/employee-asset.model';

/** EmployeeAsset — `/api/hr/employee-assets`. */
@Injectable({ providedIn: 'root' })
export class EmployeeAssetService extends RestResourceService<
  EmployeeAsset,
  EmployeeAsset,
  CreateEmployeeAssetRequest,
  UpdateEmployeeAssetRequest
> {
  constructor() {
    super('api/hr/employee-assets');
  }

  /** `GET /api/hr/employee-assets/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmployeeAsset[]> {
    return this.http.get<EmployeeAsset[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
