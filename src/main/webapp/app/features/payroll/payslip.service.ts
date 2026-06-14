import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { Payslip, PayslipSearchRequest } from './payslip.model';

/** Payslips — `/api/payroll/payslips` (lookup by id/number, per employee/period, search, PDF). */
@Injectable({ providedIn: 'root' })
export class PayslipService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/payslips');

  /** `GET /api/payroll/payslips/{id}`. */
  find(id: string): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `GET /api/payroll/payslips/by-number/{number}`. */
  byNumber(number: string): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.resourceUrl}/by-number/${encodeURIComponent(number)}`);
  }

  /** `GET /api/payroll/payslips/employees/{employeeId}`. */
  forEmployee(employeeId: string, req?: PageRequest): Observable<Page<Payslip>> {
    return this.http.get<Page<Payslip>>(`${this.resourceUrl}/employees/${encodeURIComponent(employeeId)}`, {
      params: createRequestOption(req),
    });
  }

  /** `GET /api/payroll/payslips/employees/{employeeId}/latest`. */
  latestForEmployee(employeeId: string): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.resourceUrl}/employees/${encodeURIComponent(employeeId)}/latest`);
  }

  /** `GET /api/payroll/payslips/employees/{employeeId}/ytd?year=` — year-to-date totals. */
  ytdForEmployee(employeeId: string, year: number): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.resourceUrl}/employees/${encodeURIComponent(employeeId)}/ytd`, {
      params: createRequestOption({ year }),
    });
  }

  /** `GET /api/payroll/payslips/periods/{periodId}`. */
  forPeriod(periodId: string, req?: PageRequest): Observable<Page<Payslip>> {
    return this.http.get<Page<Payslip>>(`${this.resourceUrl}/periods/${encodeURIComponent(periodId)}`, {
      params: createRequestOption(req),
    });
  }

  /** `POST /api/payroll/payslips/search`. */
  search(criteria: PayslipSearchRequest, req?: PageRequest): Observable<Page<Payslip>> {
    return this.http.post<Page<Payslip>>(`${this.resourceUrl}/search`, criteria, { params: createRequestOption(req) });
  }

  /** `GET /api/payroll/payslips/{id}/pdf` — file bytes (keeps headers for filename). */
  downloadPdf(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(id)}/pdf`, { observe: 'response', responseType: 'blob' });
  }
}
