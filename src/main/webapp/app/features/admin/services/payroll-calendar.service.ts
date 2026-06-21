import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { PayrollCalendar } from '../models/payroll-calendar.model';

/** Payroll calendars — `/api/payroll/calendars` (gated `CONFIGURE_PAYROLL_CALENDAR`). */
@Injectable({ providedIn: 'root' })
export class PayrollCalendarService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/calendars');

  /** `GET /api/payroll/calendars/active` — active calendars, each with its upcoming periods. */
  active(): Observable<PayrollCalendar[]> {
    return this.http.get<PayrollCalendar[]>(`${this.resourceUrl}/active`);
  }
}
