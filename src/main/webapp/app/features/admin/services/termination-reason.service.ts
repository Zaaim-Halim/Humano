import { Injectable } from '@angular/core';

import { RestResourceService } from 'app/core/api';

import { CreateReferenceDataRequest, ReferenceData, UpdateReferenceDataRequest } from '../models/reference-data.model';

/** TerminationReason reference data — `/api/hr/termination-reasons`. */
@Injectable({ providedIn: 'root' })
export class TerminationReasonService extends RestResourceService<
  ReferenceData,
  ReferenceData,
  CreateReferenceDataRequest,
  UpdateReferenceDataRequest
> {
  constructor() {
    super('api/hr/termination-reasons');
  }
}
