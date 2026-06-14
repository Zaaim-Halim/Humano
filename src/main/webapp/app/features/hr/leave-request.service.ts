import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateLeaveRequest, LeaveRequest, LeaveRequestSearchRequest, ProcessLeaveRequest } from './leave-request.model';

/**
 * Leave requests — `/api/hr/leave-requests`. Standard create/list/find/delete;
 * status changes go through `process` (there is no plain `PUT /{id}`), and
 * search is a GET with criteria as query params.
 */
@Injectable({ providedIn: 'root' })
export class LeaveRequestService extends RestResourceService<LeaveRequest, LeaveRequest, CreateLeaveRequest> {
  constructor() {
    super('api/hr/leave-requests');
  }

  /** Approve/reject/cancel — `PUT /api/hr/leave-requests/{id}/process`. */
  process(id: string, body: ProcessLeaveRequest): Observable<LeaveRequest> {
    return this.http.put<LeaveRequest>(`${this.resourceUrl}/${encodeURIComponent(id)}/process`, body);
  }

  /** `GET /api/hr/leave-requests/search` — criteria + pagination as query params. */
  search(criteria: LeaveRequestSearchRequest, req?: PageRequest): Observable<Page<LeaveRequest>> {
    return this.http.get<Page<LeaveRequest>>(`${this.resourceUrl}/search`, { params: createRequestOption({ ...criteria, ...req }) });
  }

  /** `GET /api/hr/leave-requests/employee/{employeeId}/search` — one employee's history. */
  searchByEmployee(employeeId: string, criteria: LeaveRequestSearchRequest, req?: PageRequest): Observable<Page<LeaveRequest>> {
    return this.http.get<Page<LeaveRequest>>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}/search`, {
      params: createRequestOption({ ...criteria, ...req }),
    });
  }

  /** Status changes go through {@link process}; the generic update is unsupported. */
  override update(): never {
    throw new Error('Use process(id, body): leave requests have no plain PUT /{id}.');
  }
}
