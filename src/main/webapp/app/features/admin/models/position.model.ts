import { AuditFields } from 'app/core/api';

/** `GET /api/hr/positions` (list) and `/{id}` (detail) — same shape. */
export interface Position extends AuditFields {
  id: string;
  name: string;
  description: string | null;
  level: string;
  unitId: string | null;
  unitName: string | null;
  parentPositionId: string | null;
  parentPositionName: string | null;
  employeeCount: number;
}

export interface CreatePositionRequest {
  /** Required. */
  name: string;
  description?: string;
  /** Required. */
  level: string;
  unitId?: string;
  parentPositionId?: string;
}

export interface UpdatePositionRequest {
  name?: string;
  description?: string;
  level?: string;
  unitId?: string;
  parentPositionId?: string;
}
