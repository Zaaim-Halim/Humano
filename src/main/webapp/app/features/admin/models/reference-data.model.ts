import { AuditFields } from 'app/core/api';

/** Tenant-configurable reference-data value (employment type, job grade, etc.). */
export interface ReferenceData extends AuditFields {
  id: string;
  code: string;
  name: string;
  active: boolean;
  notes: string | null;
}

export interface CreateReferenceDataRequest {
  /** Required. */
  code: string;
  /** Required. */
  name: string;
  active?: boolean;
  notes?: string;
}

export interface UpdateReferenceDataRequest {
  code?: string;
  name?: string;
  active?: boolean;
  notes?: string;
}
