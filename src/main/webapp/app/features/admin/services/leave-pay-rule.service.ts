import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { CreateLeavePayRuleRequest, LeavePayRule, UpdateLeavePayRuleRequest } from '../models/leave-pay-rule.model';

/**
 * Leave pay rules — `/api/payroll/leave-pay-rules` (gated `MANAGE_PAY_COMPONENTS`): how payroll
 * pays approved sick, parental and unpaid leave, per country.
 */
@Injectable({ providedIn: 'root' })
export class LeavePayRuleService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/leave-pay-rules');

  /** `GET /api/payroll/leave-pay-rules` — every rule, by country then leave type. */
  list(): Observable<LeavePayRule[]> {
    return this.http.get<LeavePayRule[]>(this.resourceUrl);
  }

  /** `POST /api/payroll/leave-pay-rules`. */
  create(body: CreateLeavePayRuleRequest): Observable<LeavePayRule> {
    return this.http.post<LeavePayRule>(this.resourceUrl, body);
  }

  /** `PUT /api/payroll/leave-pay-rules/{id}`. */
  update(id: string, body: UpdateLeavePayRuleRequest): Observable<LeavePayRule> {
    return this.http.put<LeavePayRule>(`${this.resourceUrl}/${id}`, body);
  }

  /** `DELETE /api/payroll/leave-pay-rules/{id}`. */
  delete(id: string): Observable<void> {
    return this.http.delete(`${this.resourceUrl}/${id}`).pipe(map(() => undefined));
  }
}
