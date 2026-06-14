import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateSubscriptionRequest, Subscription, UpdateSubscriptionRequest } from './subscription.model';

/** Subscriptions — `/api/billing/subscriptions` (get/create/update/cancel/delete). */
@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/billing/subscriptions');

  /** `GET /api/billing/subscriptions/{id}`. */
  find(id: string): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `POST /api/billing/subscriptions`. */
  create(body: CreateSubscriptionRequest): Observable<Subscription> {
    return this.http.post<Subscription>(this.resourceUrl, body);
  }

  /** `PUT /api/billing/subscriptions/{id}`. */
  update(id: string, body: UpdateSubscriptionRequest): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.resourceUrl}/${encodeURIComponent(id)}`, body);
  }

  /** `POST /api/billing/subscriptions/{id}/cancel?immediate=` — end now or at period end. */
  cancel(id: string, immediate = false): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.resourceUrl}/${encodeURIComponent(id)}/cancel`, null, {
      params: createRequestOption({ immediate }),
    });
  }

  /** `DELETE /api/billing/subscriptions/{id}` (destructive — confirm first). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }
}
