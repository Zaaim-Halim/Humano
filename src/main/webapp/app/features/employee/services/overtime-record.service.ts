import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import {
  CreateOvertimeRecordRequest,
  OvertimeRecord,
  OvertimeRecordSearchRequest,
  ProcessOvertimeRecordRequest,
} from '../models/overtime-record.model';

/**
 * Overtime records — `/api/hr/overtime-records`. Standard create/list/find/delete;
 * approval goes through `process` (no plain `PUT /{id}`); search is a GET.
 */
@Injectable({ providedIn: 'root' })
export class OvertimeRecordService extends RestResourceService<OvertimeRecord, OvertimeRecord, CreateOvertimeRecordRequest> {
  constructor() {
    super('api/hr/overtime-records');
  }

  /** Approve/reject — `PUT /api/hr/overtime-records/{id}/process`. */
  process(id: string, body: ProcessOvertimeRecordRequest): Observable<OvertimeRecord> {
    return this.http.put<OvertimeRecord>(`${this.resourceUrl}/${encodeURIComponent(id)}/process`, body);
  }

  /** `GET /api/hr/overtime-records/search`. */
  search(criteria: OvertimeRecordSearchRequest, req?: PageRequest): Observable<Page<OvertimeRecord>> {
    return this.http.get<Page<OvertimeRecord>>(`${this.resourceUrl}/search`, { params: createRequestOption({ ...criteria, ...req }) });
  }

  /** `GET /api/hr/overtime-records/employee/{employeeId}/search`. */
  searchByEmployee(employeeId: string, criteria: OvertimeRecordSearchRequest, req?: PageRequest): Observable<Page<OvertimeRecord>> {
    return this.http.get<Page<OvertimeRecord>>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}/search`, {
      params: createRequestOption({ ...criteria, ...req }),
    });
  }

  /** Status changes go through {@link process}; the generic update is unsupported. */
  override update(): never {
    throw new Error('Use process(id, body): overtime records have no plain PUT /{id}.');
  }
}
