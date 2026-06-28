import { Injectable } from '@angular/core';

import { RestResourceService } from 'app/core/api';

import { CreateReferenceDataRequest, ReferenceData, UpdateReferenceDataRequest } from '../models/reference-data.model';

/** EmployeeCategory reference data — `/api/hr/employee-categories`. */
@Injectable({ providedIn: 'root' })
export class EmployeeCategoryService extends RestResourceService<
  ReferenceData,
  ReferenceData,
  CreateReferenceDataRequest,
  UpdateReferenceDataRequest
> {
  constructor() {
    super('api/hr/employee-categories');
  }
}
