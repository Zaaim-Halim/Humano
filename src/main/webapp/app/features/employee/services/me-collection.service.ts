import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Address } from '../models/address.model';
import { EmergencyContact } from '../models/emergency-contact.model';

/**
 * Base for a self-service collection under `/api/me/**`.
 *
 * <p>Satisfies the same structural contract as the HR-side services so the generic
 * `EmployeeCollectionComponent` can drive it unchanged — but the URLs carry no employee
 * id at all. The owner is derived server-side from the security context, which is what
 * makes these endpoints safe for a plain `ROLE_EMPLOYEE`; the `employeeId` argument and
 * body property the collection component supplies are therefore dropped here rather than
 * sent and ignored.
 */
abstract class MeCollectionService<T> {
  protected readonly http = inject(HttpClient);
  private readonly resourceUrl: string;

  protected constructor(path: string) {
    this.resourceUrl = inject(ApplicationConfigService).getEndpointFor(path);
  }

  /** The caller's own records. The id is ignored — the server uses the authenticated user. */
  byEmployee(): Observable<T[]> {
    return this.http.get<T[]>(this.resourceUrl);
  }

  create(body: Record<string, unknown>): Observable<T> {
    return this.http.post<T>(this.resourceUrl, stripEmployeeId(body));
  }

  update(id: string, body: Record<string, unknown>): Observable<T> {
    return this.http.put<T>(`${this.resourceUrl}/${encodeURIComponent(id)}`, stripEmployeeId(body));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }
}

/** The self endpoints reject the concept of a caller-supplied owner, so never send one. */
function stripEmployeeId(body: Record<string, unknown>): Record<string, unknown> {
  const { employeeId, ...rest } = body;
  return rest;
}

/** Own addresses — `/api/me/addresses`. */
@Injectable({ providedIn: 'root' })
export class MeAddressService extends MeCollectionService<Address> {
  constructor() {
    super('api/me/addresses');
  }
}

/** Own emergency contacts — `/api/me/emergency-contacts`. */
@Injectable({ providedIn: 'root' })
export class MeEmergencyContactService extends MeCollectionService<EmergencyContact> {
  constructor() {
    super('api/me/emergency-contacts');
  }
}
