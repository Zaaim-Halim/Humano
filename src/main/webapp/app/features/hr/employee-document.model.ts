import { AuditFields } from 'app/core/api';

/** `GET /api/hr/employee-documents` (list) and `/{id}` (detail). */
export interface EmployeeDocument extends AuditFields {
  id: string;
  type: string | null;
  filePath: string | null;
  employeeId: string;
  employeeName: string | null;
}

export interface CreateEmployeeDocumentRequest {
  type?: string;
}

export interface UpdateEmployeeDocumentRequest {
  type?: string;
}
