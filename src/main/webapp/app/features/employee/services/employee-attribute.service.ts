import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EmployeeAttribute } from '../models/employee-attribute.model';

/**
 * Employee custom attributes — nested under the employee resource
 * (`/api/hr/employees/{id}/attributes`). Read returns the full set; write is a
 * replace-all (`PUT`) — send the complete list, omitted keys are removed.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeAttributeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'api/hr/employees';

  /** `GET /api/hr/employees/{employeeId}/attributes`. */
  getForEmployee(employeeId: string): Observable<EmployeeAttribute[]> {
    return this.http.get<EmployeeAttribute[]>(`${this.baseUrl}/${encodeURIComponent(employeeId)}/attributes`);
  }

  /** `PUT /api/hr/employees/{employeeId}/attributes` — replace the whole set. */
  replaceForEmployee(employeeId: string, attributes: EmployeeAttribute[]): Observable<EmployeeAttribute[]> {
    return this.http.put<EmployeeAttribute[]>(`${this.baseUrl}/${encodeURIComponent(employeeId)}/attributes`, { attributes });
  }
}
