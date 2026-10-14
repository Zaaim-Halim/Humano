import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { SubscriptionType } from '../models/enums/subscription-type.enum';
import { SubscriptionPlan } from '../models/subscription-plan.model';

/**
 * Subscription plans (read-only) — `/api/billing/plans`. Plans are managed from the owner console
 * (`PlatformBillingService`).
 */
@Injectable({ providedIn: 'root' })
export class SubscriptionPlanService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/billing/plans');

  /** `GET /api/billing/plans` — paged. */
  query(req?: PageRequest): Observable<Page<SubscriptionPlan>> {
    return this.http.get<Page<SubscriptionPlan>>(this.resourceUrl, { params: createRequestOption(req) });
  }

  /** `GET /api/billing/plans/active` — active plans (unpaged). */
  listActive(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${this.resourceUrl}/active`);
  }

  /** `GET /api/billing/plans/by-type/{type}`. */
  byType(type: SubscriptionType): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${this.resourceUrl}/by-type/${encodeURIComponent(type)}`);
  }

  /** `GET /api/billing/plans/{id}`. */
  find(id: string): Observable<SubscriptionPlan> {
    return this.http.get<SubscriptionPlan>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }
}
