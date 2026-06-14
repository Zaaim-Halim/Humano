import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateTimesheetRequest, Timesheet, TimesheetSearchRequest, UpdateTimesheetRequest } from './timesheet.model';

/** Timesheets — `/api/hr/timesheets` (standard CRUD + search, incl. per-employee). */
@Injectable({ providedIn: 'root' })
export class TimesheetService extends RestResourceService<Timesheet, Timesheet, CreateTimesheetRequest, UpdateTimesheetRequest> {
  constructor() {
    super('api/hr/timesheets');
  }

  /** `GET /api/hr/timesheets/search`. */
  search(criteria: TimesheetSearchRequest, req?: PageRequest): Observable<Page<Timesheet>> {
    return this.http.get<Page<Timesheet>>(`${this.resourceUrl}/search`, { params: createRequestOption({ ...criteria, ...req }) });
  }

  /** `GET /api/hr/timesheets/employee/{employeeId}/search`. */
  searchByEmployee(employeeId: string, criteria: TimesheetSearchRequest, req?: PageRequest): Observable<Page<Timesheet>> {
    return this.http.get<Page<Timesheet>>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}/search`, {
      params: createRequestOption({ ...criteria, ...req }),
    });
  }
}
