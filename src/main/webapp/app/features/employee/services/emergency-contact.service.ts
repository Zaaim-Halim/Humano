import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmergencyContactRequest, EmergencyContact, UpdateEmergencyContactRequest } from '../models/emergency-contact.model';

/** EmergencyContact — `/api/hr/emergency-contacts`. */
@Injectable({ providedIn: 'root' })
export class EmergencyContactService extends RestResourceService<
  EmergencyContact,
  EmergencyContact,
  CreateEmergencyContactRequest,
  UpdateEmergencyContactRequest
> {
  constructor() {
    super('api/hr/emergency-contacts');
  }

  /** `GET /api/hr/emergency-contacts/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<EmergencyContact[]> {
    return this.http.get<EmergencyContact[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
