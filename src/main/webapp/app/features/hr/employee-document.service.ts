import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest, RestResourceService } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { CreateEmployeeDocumentRequest, EmployeeDocument, UpdateEmployeeDocumentRequest } from './employee-document.model';

/**
 * Employee documents — `/api/hr/employee-documents`. Created against an
 * employee; the binary is attached separately via `uploadFile`, and downloaded
 * as a blob.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeDocumentService extends RestResourceService<
  EmployeeDocument,
  EmployeeDocument,
  CreateEmployeeDocumentRequest,
  UpdateEmployeeDocumentRequest
> {
  constructor() {
    super('api/hr/employee-documents');
  }

  /** `POST /api/hr/employee-documents/employee/{employeeId}` — create metadata. */
  createForEmployee(employeeId: string, body: CreateEmployeeDocumentRequest): Observable<EmployeeDocument> {
    return this.http.post<EmployeeDocument>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`, body);
  }

  /** `PUT /api/hr/employee-documents/{id}/file` — attach/replace the file. */
  uploadFile(id: string, file: File): Observable<EmployeeDocument> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<EmployeeDocument>(`${this.resourceUrl}/${encodeURIComponent(id)}/file`, form);
  }

  /** `GET /api/hr/employee-documents/employee/{employeeId}` — one employee's docs. */
  forEmployee(employeeId: string, req?: PageRequest): Observable<Page<EmployeeDocument>> {
    return this.http.get<Page<EmployeeDocument>>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`, {
      params: createRequestOption(req),
    });
  }

  /** `GET /api/hr/employee-documents/{id}/download` — file bytes (keeps headers for filename). */
  download(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(id)}/download`, { observe: 'response', responseType: 'blob' });
  }

  /** Documents are created via {@link createForEmployee}; the unscoped create is unsupported. */
  override create(): never {
    throw new Error(
      'Use createForEmployee(employeeId, body): documents are created at POST /api/hr/employee-documents/employee/{employeeId}.',
    );
  }
}
