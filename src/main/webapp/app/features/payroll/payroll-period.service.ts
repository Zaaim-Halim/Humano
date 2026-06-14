import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { GeneratePayrollPeriodsRequest, PayrollPeriod } from './payroll-period.model';

/** Payroll periods — `/api/payroll/periods` (generate, list per calendar, open/close/reopen). */
@Injectable({ providedIn: 'root' })
export class PayrollPeriodService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/periods');

  /** `POST /api/payroll/periods/generate`. */
  generate(body: GeneratePayrollPeriodsRequest): Observable<PayrollPeriod[]> {
    return this.http.post<PayrollPeriod[]>(`${this.resourceUrl}/generate`, body);
  }

  /** `GET /api/payroll/periods/calendars/{calendarId}` — paged periods for a calendar. */
  forCalendar(calendarId: string, req?: PageRequest): Observable<Page<PayrollPeriod>> {
    return this.http.get<Page<PayrollPeriod>>(`${this.resourceUrl}/calendars/${encodeURIComponent(calendarId)}`, {
      params: createRequestOption(req),
    });
  }

  /** `GET /api/payroll/periods/calendars/{calendarId}/open` — currently-open periods. */
  openForCalendar(calendarId: string): Observable<PayrollPeriod[]> {
    return this.http.get<PayrollPeriod[]>(`${this.resourceUrl}/calendars/${encodeURIComponent(calendarId)}/open`);
  }

  /** `POST /api/payroll/periods/{id}/close`. */
  close(id: string): Observable<PayrollPeriod> {
    return this.http.post<PayrollPeriod>(`${this.resourceUrl}/${encodeURIComponent(id)}/close`, {});
  }

  /** `POST /api/payroll/periods/{id}/reopen?reason=`. */
  reopen(id: string, reason: string): Observable<PayrollPeriod> {
    return this.http.post<PayrollPeriod>(`${this.resourceUrl}/${encodeURIComponent(id)}/reopen`, null, {
      params: createRequestOption({ reason }),
    });
  }

  /** `PUT /api/payroll/periods/{id}/payment-date?paymentDate=`. */
  updatePaymentDate(id: string, paymentDate: string): Observable<PayrollPeriod> {
    return this.http.put<PayrollPeriod>(`${this.resourceUrl}/${encodeURIComponent(id)}/payment-date`, null, {
      params: createRequestOption({ paymentDate }),
    });
  }
}
