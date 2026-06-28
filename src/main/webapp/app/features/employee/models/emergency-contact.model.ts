import { AuditFields } from 'app/core/api';

/** EmergencyContact — `/api/hr/emergency-contacts`. */
export interface EmergencyContact extends AuditFields {
  id: string;
  employeeId: string | null;
  name: string | null;
  relationship: string | null;
  phone: string | null;
  email: string | null;
}

export interface CreateEmergencyContactRequest {
  /** Required. */
  employeeId: string;
  name?: string;
  relationship?: string;
  phone?: string;
  email?: string;
}

export interface UpdateEmergencyContactRequest {
  name?: string;
  relationship?: string;
  phone?: string;
  email?: string;
}
