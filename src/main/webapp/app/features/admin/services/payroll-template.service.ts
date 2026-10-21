import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { ConfigurePayrollTemplateRequest, PayrollTemplate, PayrollTemplateAdoptionResult } from '../models/payroll-template.model';

/**
 * Statutory payroll templates — `/api/payroll/templates` (gated `MANAGE_TAX_BRACKETS`). Backs the
 * Payroll → Templates screens: browse the shipped catalog, set a template's employer settings,
 * enable it, and review new versions.
 */
@Injectable({ providedIn: 'root' })
export class PayrollTemplateService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);
  private readonly url = this.config.getEndpointFor('api/payroll/templates');

  /** `GET /api/payroll/templates` — every shipped template, with this tenant's adoption. */
  list(): Observable<PayrollTemplate[]> {
    return this.http.get<PayrollTemplate[]>(this.url);
  }

  /** `GET /api/payroll/templates/{id}`. */
  get(id: string): Observable<PayrollTemplate> {
    return this.http.get<PayrollTemplate>(`${this.url}/${encodeURIComponent(id)}`);
  }

  /** `PUT /api/payroll/templates/{id}/adoption` — save the employer settings and enable or disable. */
  configure(id: string, body: ConfigurePayrollTemplateRequest): Observable<PayrollTemplateAdoptionResult> {
    return this.http.put<PayrollTemplateAdoptionResult>(`${this.url}/${encodeURIComponent(id)}/adoption`, body);
  }

  /** `POST /api/payroll/templates/{id}/adoption/acknowledge` — the shipped version has been reviewed. */
  acknowledge(id: string): Observable<PayrollTemplate> {
    return this.http.post<PayrollTemplate>(`${this.url}/${encodeURIComponent(id)}/adoption/acknowledge`, null);
  }
}
