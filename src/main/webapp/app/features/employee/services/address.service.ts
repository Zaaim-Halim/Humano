import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateAddressRequest, Address, UpdateAddressRequest } from '../models/address.model';

/** Address — `/api/hr/addresses`. */
@Injectable({ providedIn: 'root' })
export class AddressService extends RestResourceService<Address, Address, CreateAddressRequest, UpdateAddressRequest> {
  constructor() {
    super('api/hr/addresses');
  }

  /** `GET /api/hr/addresses/employee/{employeeId}` — records for an employee. */
  byEmployee(employeeId: string): Observable<Address[]> {
    return this.http.get<Address[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }
}
