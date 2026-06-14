import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { PayrollResult } from '../models/payroll-result.model';
import {
  ApprovePayrollRunRequest,
  InitiatePayrollRunRequest,
  PayrollRun,
  PayrollRunSummary,
  RecalculatePayrollRequest,
} from '../models/payroll-run.model';
import { Payslip } from '../models/payslip.model';

/**
 * Payroll runs — `/api/payroll/runs`. A process resource, not CRUD: a run moves
 * Draft → Calculated → Approved → Posted via dedicated actions; there is no
 * list/plain-get (fetch state through `summary`). `approve`/`post` are financial
 * mutations — guard them behind a confirm dialog in the UI.
 */
@Injectable({ providedIn: 'root' })
export class PayrollRunService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/runs');

  /** `POST /api/payroll/runs` — initiate a run for a period. */
  initiate(body: InitiatePayrollRunRequest): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(this.resourceUrl, body);
  }

  /** `POST /api/payroll/runs/{id}/calculate`. */
  calculate(id: string): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.resourceUrl}/${encodeURIComponent(id)}/calculate`, {});
  }

  /** `POST /api/payroll/runs/{id}/approve` (financial — confirm first). */
  approve(id: string, body: ApprovePayrollRunRequest): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.resourceUrl}/${encodeURIComponent(id)}/approve`, body);
  }

  /** `POST /api/payroll/runs/{id}/post` (financial — confirm first). */
  postRun(id: string): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.resourceUrl}/${encodeURIComponent(id)}/post`, {});
  }

  /** `POST /api/payroll/runs/{id}/recalculate`. */
  recalculate(id: string, body: RecalculatePayrollRequest): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.resourceUrl}/${encodeURIComponent(id)}/recalculate`, body);
  }

  /** `GET /api/payroll/runs/{id}/summary` — totals, by-department, variance vs previous. */
  summary(id: string): Observable<PayrollRunSummary> {
    return this.http.get<PayrollRunSummary>(`${this.resourceUrl}/${encodeURIComponent(id)}/summary`);
  }

  /** `GET /api/payroll/runs/{id}/results` — per-employee line items (unpaged list). */
  results(id: string): Observable<PayrollResult[]> {
    return this.http.get<PayrollResult[]>(`${this.resourceUrl}/${encodeURIComponent(id)}/results`);
  }

  /** `POST /api/payroll/runs/{id}/generate-payslips`. */
  generatePayslips(id: string): Observable<Payslip[]> {
    return this.http.post<Payslip[]>(`${this.resourceUrl}/${encodeURIComponent(id)}/generate-payslips`, {});
  }

  /** `GET /api/payroll/runs/{id}/payslips/{employeeId}`. */
  payslipFor(id: string, employeeId: string): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.resourceUrl}/${encodeURIComponent(id)}/payslips/${encodeURIComponent(employeeId)}`);
  }

  /** `GET /api/payroll/runs/{id}/payslips/{employeeId}/pdf`. */
  payslipPdfFor(id: string, employeeId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(id)}/payslips/${encodeURIComponent(employeeId)}/pdf`, {
      observe: 'response',
      responseType: 'blob',
    });
  }
}
