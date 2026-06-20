import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RestResourceService } from 'app/core/api';

import { CreateEmployeeDocumentRequest, EmployeeDocument, UpdateEmployeeDocumentRequest } from '../models/employee-document.model';

/**
 * Employee documents — `/api/hr/employee-documents`. A document is created by
 * uploading its file and metadata together (multipart), and downloaded as a blob.
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

  /**
   * `POST /api/hr/employee-documents/employee/{employeeId}` — create a document.
   * The endpoint is multipart with two parts: a JSON `metadata` part and the
   * binary `file` part.
   */
  uploadForEmployee(employeeId: string, file: File, metadata: CreateEmployeeDocumentRequest = {}): Observable<EmployeeDocument> {
    const form = new FormData();
    form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    form.append('file', file);
    return this.http.post<EmployeeDocument>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`, form);
  }

  /** `PUT /api/hr/employee-documents/{id}/file` — replace the file of an existing document. */
  replaceFile(id: string, file: File): Observable<EmployeeDocument> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<EmployeeDocument>(`${this.resourceUrl}/${encodeURIComponent(id)}/file`, form);
  }

  /** `GET /api/hr/employee-documents/employee/{employeeId}` — one employee's docs (unpaged list). */
  forEmployee(employeeId: string): Observable<EmployeeDocument[]> {
    return this.http.get<EmployeeDocument[]>(`${this.resourceUrl}/employee/${encodeURIComponent(employeeId)}`);
  }

  /** `GET /api/hr/employee-documents/{id}/download` — file bytes (keeps headers for filename). */
  download(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(id)}/download`, { observe: 'response', responseType: 'blob' });
  }

  /** Documents are created via {@link uploadForEmployee}; the unscoped create is unsupported. */
  override create(): never {
    throw new Error(
      'Use uploadForEmployee(employeeId, file, metadata): documents are created at POST /api/hr/employee-documents/employee/{employeeId}.',
    );
  }
}
