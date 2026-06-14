import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { PayrollResult } from './payroll-result.model';
import { Payslip } from './payslip.model';

/** Payroll results — `/api/payroll/results` (fetch one + generate its payslip). */
@Injectable({ providedIn: 'root' })
export class PayrollResultService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/results');

  /** `GET /api/payroll/results/{id}`. */
  find(id: string): Observable<PayrollResult> {
    return this.http.get<PayrollResult>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `POST /api/payroll/results/{id}/generate-payslip`. */
  generatePayslip(id: string): Observable<Payslip> {
    return this.http.post<Payslip>(`${this.resourceUrl}/${encodeURIComponent(id)}/generate-payslip`, {});
  }
}
