import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateDepartmentRequest, Department, UpdateDepartmentRequest } from '../models/department.model';

/** Departments — `/api/hr/departments` (standard CRUD + head assignment). */
@Injectable({ providedIn: 'root' })
export class DepartmentService extends RestResourceService<Department, Department, CreateDepartmentRequest, UpdateDepartmentRequest> {
  constructor() {
    super('api/hr/departments');
  }

  /** `GET /api/hr/departments/exists?name=` — name uniqueness check. */
  existsByName(name: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.resourceUrl}/exists`, { params: createRequestOption({ name }) });
  }

  /** `PUT /api/hr/departments/{id}/head/{headId}` — assign a department head. */
  assignHead(id: string, headId: string): Observable<Department> {
    return this.http.put<Department>(`${this.resourceUrl}/${encodeURIComponent(id)}/head/${encodeURIComponent(headId)}`, {});
  }

  /** `DELETE /api/hr/departments/{id}/head` — clear the department head. */
  removeHead(id: string): Observable<Department> {
    return this.http.delete<Department>(`${this.resourceUrl}/${encodeURIComponent(id)}/head`);
  }
}
