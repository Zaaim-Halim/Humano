import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { SubscriptionType } from '../models/enums/subscription-type.enum';
import { CreateSubscriptionPlanRequest, SubscriptionPlan, UpdateSubscriptionPlanRequest } from '../models/subscription-plan.model';

/** Subscription plans — `/api/billing/plans` (paged list, active/by-type lookups, CRUD, (de)activate). */
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

  /** `POST /api/billing/plans`. */
  create(body: CreateSubscriptionPlanRequest): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(this.resourceUrl, body);
  }

  /** `PUT /api/billing/plans/{id}`. */
  update(id: string, body: UpdateSubscriptionPlanRequest): Observable<SubscriptionPlan> {
    return this.http.put<SubscriptionPlan>(`${this.resourceUrl}/${encodeURIComponent(id)}`, body);
  }

  /** `POST /api/billing/plans/{id}/activate`. */
  activate(id: string): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(`${this.resourceUrl}/${encodeURIComponent(id)}/activate`, {});
  }

  /** `POST /api/billing/plans/{id}/deactivate`. */
  deactivate(id: string): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(`${this.resourceUrl}/${encodeURIComponent(id)}/deactivate`, {});
  }
}
