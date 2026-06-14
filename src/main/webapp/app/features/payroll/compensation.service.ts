import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import {
  Compensation,
  CompensationSearchRequest,
  CreateCompensationRequest,
  SalaryAdjustmentRequest,
  SalaryHistory,
} from './compensation.model';

/** Compensation — `/api/payroll/compensations` (create/adjust, history, by-department, search). */
@Injectable({ providedIn: 'root' })
export class CompensationService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/payroll/compensations');

  /** `POST /api/payroll/compensations`. */
  create(body: CreateCompensationRequest): Observable<Compensation> {
    return this.http.post<Compensation>(this.resourceUrl, body);
  }

  /** `POST /api/payroll/compensations/adjust` — salary change (amount XOR percentage). */
  adjust(body: SalaryAdjustmentRequest): Observable<Compensation> {
    return this.http.post<Compensation>(`${this.resourceUrl}/adjust`, body);
  }

  /** `GET /api/payroll/compensations/employees/{employeeId}/history`. */
  history(employeeId: string): Observable<SalaryHistory> {
    return this.http.get<SalaryHistory>(`${this.resourceUrl}/employees/${encodeURIComponent(employeeId)}/history`);
  }

  /** `GET /api/payroll/compensations/departments/{departmentId}`. */
  byDepartment(departmentId: string, req?: PageRequest): Observable<Page<Compensation>> {
    return this.http.get<Page<Compensation>>(`${this.resourceUrl}/departments/${encodeURIComponent(departmentId)}`, {
      params: createRequestOption(req),
    });
  }

  /** `GET /api/payroll/compensations/departments/{departmentId}/cost?asOfDate=` — cost buckets. */
  departmentCost(departmentId: string, asOfDate: string): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.resourceUrl}/departments/${encodeURIComponent(departmentId)}/cost`, {
      params: createRequestOption({ asOfDate }),
    });
  }

  /** `POST /api/payroll/compensations/search`. */
  search(criteria: CompensationSearchRequest, req?: PageRequest): Observable<Page<Compensation>> {
    return this.http.post<Page<Compensation>>(`${this.resourceUrl}/search`, criteria, { params: createRequestOption(req) });
  }

  /** `POST /api/payroll/compensations/employees/{employeeId}/search`. */
  searchByEmployee(employeeId: string, criteria: CompensationSearchRequest, req?: PageRequest): Observable<Page<Compensation>> {
    return this.http.post<Page<Compensation>>(`${this.resourceUrl}/employees/${encodeURIComponent(employeeId)}/search`, criteria, {
      params: createRequestOption(req),
    });
  }
}
