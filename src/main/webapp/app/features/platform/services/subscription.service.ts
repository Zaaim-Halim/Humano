import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { Subscription } from '../models/subscription.model';

/**
 * The signed-in customer's own subscription — `/api/billing/subscriptions`: read it, or cancel it
 * (billing admins). Plan changes go through the platform (`PlatformBillingService`).
 */
@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/billing/subscriptions');

  /** `GET /api/billing/subscriptions/{id}`. */
  find(id: string): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `POST /api/billing/subscriptions/{id}/cancel?immediate=` — end now or at period end. */
  cancel(id: string, immediate = false): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.resourceUrl}/${encodeURIComponent(id)}/cancel`, null, {
      params: createRequestOption({ immediate }),
    });
  }
}
