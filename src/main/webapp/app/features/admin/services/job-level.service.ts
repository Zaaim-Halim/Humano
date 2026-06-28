import { Injectable } from '@angular/core';

import { RestResourceService } from 'app/core/api';

import { CreateReferenceDataRequest, ReferenceData, UpdateReferenceDataRequest } from '../models/reference-data.model';

/** JobLevel reference data — `/api/hr/job-levels`. */
@Injectable({ providedIn: 'root' })
export class JobLevelService extends RestResourceService<
  ReferenceData,
  ReferenceData,
  CreateReferenceDataRequest,
  UpdateReferenceDataRequest
> {
  constructor() {
    super('api/hr/job-levels');
  }
}
