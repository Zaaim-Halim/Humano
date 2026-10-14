import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';
import { SalaryHistory } from 'app/features/admin/models/compensation.model';
import { Payslip } from 'app/features/admin/models/payslip.model';

import { EmployeeTraining } from '../models/employee-training.model';
import { PerformanceReview } from '../models/performance-review.model';
import { TimeClockDay, TimeClockRequest } from '../models/time-clock.model';

/** The signed-in employee's own pay, payslips, reviews, trainings and time clock — `/api/me/**`. */
@Injectable({ providedIn: 'root' })
export class MeWorkService {
  private readonly http = inject(HttpClient);
  private readonly url = inject(ApplicationConfigService).getEndpointFor('api/me');

  salaryHistory(): Observable<SalaryHistory> {
    return this.http.get<SalaryHistory>(`${this.url}/compensation/history`);
  }

  payslips(req?: PageRequest): Observable<Page<Payslip>> {
    return this.http.get<Page<Payslip>>(`${this.url}/payslips`, { params: createRequestOption(req) });
  }

  payslip(id: string): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.url}/payslips/${encodeURIComponent(id)}`);
  }

  payslipYtd(year: number): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.url}/payslips/ytd`, { params: createRequestOption({ year }) });
  }

  payslipPdf(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.url}/payslips/${encodeURIComponent(id)}/pdf`, { observe: 'response', responseType: 'blob' });
  }

  performanceReviews(req?: PageRequest): Observable<Page<PerformanceReview>> {
    return this.http.get<Page<PerformanceReview>>(`${this.url}/performance-reviews`, { params: createRequestOption(req) });
  }

  // ---- time clock (`/api/me/attendance`) — the server stamps every time

  clockToday(): Observable<TimeClockDay> {
    return this.http.get<TimeClockDay>(`${this.url}/attendance/today`);
  }

  clockDays(from: string, to: string): Observable<TimeClockDay[]> {
    return this.http.get<TimeClockDay[]>(`${this.url}/attendance`, { params: createRequestOption({ from, to }) });
  }

  clockIn(body: TimeClockRequest = {}): Observable<TimeClockDay> {
    return this.http.post<TimeClockDay>(`${this.url}/attendance/clock-in`, body);
  }

  startBreak(body: TimeClockRequest = {}): Observable<TimeClockDay> {
    return this.http.post<TimeClockDay>(`${this.url}/attendance/break/start`, body);
  }

  endBreak(): Observable<TimeClockDay> {
    return this.http.post<TimeClockDay>(`${this.url}/attendance/break/end`, {});
  }

  clockOut(body: TimeClockRequest = {}): Observable<TimeClockDay> {
    return this.http.post<TimeClockDay>(`${this.url}/attendance/clock-out`, body);
  }

  trainings(req?: PageRequest): Observable<Page<EmployeeTraining>> {
    return this.http.get<Page<EmployeeTraining>>(`${this.url}/trainings`, { params: createRequestOption(req) });
  }
}
