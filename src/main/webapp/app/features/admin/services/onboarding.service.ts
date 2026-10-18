import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';

import { EmployeeProcessStatus } from '../models/enums/employee-process-status.enum';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import {
  CompleteTaskRequest,
  EmployeeProcessSummary,
  InitiateOnboardingRequest,
  OnboardingChecklist,
  OnboardingProcess,
  UpdateOnboardingChecklistRequest,
} from '../models/onboarding.model';

/**
 * Employee onboarding — `/api/hr/workflows/onboarding`. A process resource:
 * starting one creates a dated checklist for the new hire; completing every
 * task completes the process. Reads need `VIEW_ONBOARDING_PROGRESS` or
 * `MANAGE_ONBOARDING`, writes need `MANAGE_ONBOARDING`.
 */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/onboarding');
  private readonly checklistUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/onboarding-checklist');

  /** `GET /onboarding-checklist` — default length and checklist (built-in until customised). */
  checklist(): Observable<OnboardingChecklist> {
    return this.http.get<OnboardingChecklist>(this.checklistUrl);
  }

  /** `PUT /onboarding-checklist` — replace the default length and the whole checklist. */
  saveChecklist(body: UpdateOnboardingChecklistRequest): Observable<OnboardingChecklist> {
    return this.http.put<OnboardingChecklist>(this.checklistUrl, body);
  }

  /** `GET /` — active (planned, in-progress or delayed) onboardings. */
  listActive(req?: PageRequest): Observable<Page<EmployeeProcessSummary>> {
    return this.list({}, req);
  }

  /**
   * `GET /?status=&employeeId=` — onboardings in the given statuses (active when none),
   * optionally for one employee.
   */
  list(filter: { status?: EmployeeProcessStatus[]; employeeId?: string }, req?: PageRequest): Observable<Page<EmployeeProcessSummary>> {
    return this.http.get<Page<EmployeeProcessSummary>>(this.resourceUrl, {
      params: createRequestOption({
        ...req,
        ...(filter.status?.length ? { status: filter.status } : {}),
        ...(filter.employeeId ? { employeeId: filter.employeeId } : {}),
      }),
    });
  }

  /** `GET /{processId}` — one process with its checklist, in any status. */
  find(processId: string): Observable<OnboardingProcess> {
    return this.http.get<OnboardingProcess>(`${this.resourceUrl}/${encodeURIComponent(processId)}`);
  }

  /** `POST /` — start onboarding for an employee (rejected if one is already active). */
  initiate(body: InitiateOnboardingRequest): Observable<OnboardingProcess> {
    return this.http.post<OnboardingProcess>(this.resourceUrl, body);
  }

  /** `POST /{processId}/tasks/complete` — returns the updated process. */
  completeTask(processId: string, body: CompleteTaskRequest): Observable<OnboardingProcess> {
    return this.http.post<OnboardingProcess>(`${this.resourceUrl}/${encodeURIComponent(processId)}/tasks/complete`, body);
  }

  /** `POST /{processId}/cancel?reason=` — `reason` is required. */
  cancel(processId: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(processId)}/cancel`, null, {
      params: createRequestOption({ reason }),
    });
  }

  /** `POST /{processId}/escalate` — notify assignees' managers about overdue tasks. */
  escalate(processId: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(processId)}/escalate`, null);
  }
}
