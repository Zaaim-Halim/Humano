import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { EmployeeProcessStatus } from '../models/enums/employee-process-status.enum';
import { CompleteTaskRequest, EmployeeProcessSummary } from '../models/onboarding.model';
import { InitiateOffboardingRequest, OffboardingProcess } from '../models/offboarding.model';

/**
 * Employee offboarding — `/api/hr/workflows/offboarding`. Starting one marks the employee as
 * leaving and creates their hand-over checklist; the day after their last working day they're
 * terminated automatically. Reads need `VIEW_OFFBOARDING` or `MANAGE_OFFBOARDING`, writes
 * `MANAGE_OFFBOARDING`.
 */
@Injectable({ providedIn: 'root' })
export class OffboardingService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/offboarding');

  /** `GET /?status=&employeeId=` — offboardings in the given statuses (active when none), optionally for one employee. */
  list(filter: { status?: EmployeeProcessStatus[]; employeeId?: string }, req?: PageRequest): Observable<Page<EmployeeProcessSummary>> {
    return this.http.get<Page<EmployeeProcessSummary>>(this.resourceUrl, {
      params: createRequestOption({
        ...req,
        ...(filter.status?.length ? { status: filter.status } : {}),
        ...(filter.employeeId ? { employeeId: filter.employeeId } : {}),
      }),
    });
  }

  /** `GET /{processId}` — one offboarding with its checklist and settlement. */
  find(processId: string): Observable<OffboardingProcess> {
    return this.http.get<OffboardingProcess>(`${this.resourceUrl}/${encodeURIComponent(processId)}`);
  }

  /** `POST /` — start offboarding (a last day in the past ends employment straight away). */
  initiate(body: InitiateOffboardingRequest): Observable<OffboardingProcess> {
    return this.http.post<OffboardingProcess>(this.resourceUrl, body);
  }

  /** `POST /{processId}/tasks/complete` — returns the updated process. */
  completeTask(processId: string, body: CompleteTaskRequest): Observable<OffboardingProcess> {
    return this.http.post<OffboardingProcess>(`${this.resourceUrl}/${encodeURIComponent(processId)}/tasks/complete`, body);
  }

  /** `POST /{processId}/cancel?reason=` — restores their status unless they've already left. */
  cancel(processId: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(processId)}/cancel`, null, {
      params: createRequestOption({ reason }),
    });
  }

  /** `POST /{processId}/escalate` — remind the owners' managers about overdue tasks. */
  escalate(processId: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(processId)}/escalate`, null);
  }
}
