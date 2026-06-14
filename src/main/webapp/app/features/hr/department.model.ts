import { AuditFields } from 'app/core/api';

/** `GET /api/hr/departments` (list) and `/{id}` (detail) — same shape. */
export interface Department extends AuditFields {
  id: string;
  name: string;
  description: string | null;
  headId: string | null;
  headName: string | null;
  employeeCount: number;
}

export interface CreateDepartmentRequest {
  /** Required. */
  name: string;
  description?: string;
  headId?: string;
}

export interface UpdateDepartmentRequest {
  name?: string;
  description?: string;
  headId?: string;
}
