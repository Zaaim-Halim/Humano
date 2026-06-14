import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { Attendance, AttendanceSearchRequest, CreateAttendanceRequest, UpdateAttendanceRequest } from './attendance.model';

/** Attendance — `/api/hr/attendances` (standard CRUD + search; events sub-API exists). */
@Injectable({ providedIn: 'root' })
export class AttendanceService extends RestResourceService<Attendance, Attendance, CreateAttendanceRequest, UpdateAttendanceRequest> {
  constructor() {
    super('api/hr/attendances');
  }

  /** `GET /api/hr/attendances/search`. */
  search(criteria: AttendanceSearchRequest, req?: PageRequest): Observable<Page<Attendance>> {
    return this.http.get<Page<Attendance>>(`${this.resourceUrl}/search`, { params: createRequestOption({ ...criteria, ...req }) });
  }

  /** `GET /api/hr/attendances/employee/{employeeId}/search`. */
  searchByEmployee(employeeId: string, criteria: AttendanceSearchRequest, req?: PageRequest): Observable<Page<Attendance>> {
    return this.http.get<Page<Attendance>>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}/search`, {
      params: createRequestOption({ ...criteria, ...req }),
    });
  }

  // Attendance-event endpoints (POST /events, GET /events/search, …) are not
  // modelled yet — add when a screen needs the raw event stream.
}
