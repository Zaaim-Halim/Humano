import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Subscription } from './subscription.model';

/**
 * Self-service billing (Phase 4.7) — `/api/billing/me`. The backend currently
 * exposes only the current subscription; the richer self-serve surface from the
 * spec (seat usage, payment method, own invoices/PDF) is not implemented.
 */
@Injectable({ providedIn: 'root' })
export class MeBillingService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/billing/me');

  /** `GET /api/billing/me/current-subscription`. */
  currentSubscription(): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.resourceUrl}/current-subscription`);
  }

  // TODO: backend endpoint missing — seat usage, payment method, and own
  // invoices/PDF (`/api/me/billing/*` in the spec) are not implemented.
}
