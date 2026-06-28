import { Injectable } from '@angular/core';

import { RestResourceService } from 'app/core/api';

import { CreateReferenceDataRequest, ReferenceData, UpdateReferenceDataRequest } from '../models/reference-data.model';

/** JobGrade reference data — `/api/hr/job-grades`. */
@Injectable({ providedIn: 'root' })
export class JobGradeService extends RestResourceService<
  ReferenceData,
  ReferenceData,
  CreateReferenceDataRequest,
  UpdateReferenceDataRequest
> {
  constructor() {
    super('api/hr/job-grades');
  }
}
