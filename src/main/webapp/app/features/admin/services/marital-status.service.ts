import { Injectable } from '@angular/core';

import { RestResourceService } from 'app/core/api';

import { CreateReferenceDataRequest, ReferenceData, UpdateReferenceDataRequest } from '../models/reference-data.model';

/** MaritalStatus reference data — `/api/hr/marital-statuses`. */
@Injectable({ providedIn: 'root' })
export class MaritalStatusService extends RestResourceService<
  ReferenceData,
  ReferenceData,
  CreateReferenceDataRequest,
  UpdateReferenceDataRequest
> {
  constructor() {
    super('api/hr/marital-statuses');
  }
}
