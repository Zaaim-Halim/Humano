import { AuditFields } from 'app/core/api';

/** EmployeeMedicalProfile — `/api/hr/employee-medical-profiles`. */
export interface EmployeeMedicalProfile extends AuditFields {
  id: string;
  employeeId: string | null;
  bloodType: string | null;
  allergies: string | null;
  emergencyNotes: string | null;
}

export interface CreateEmployeeMedicalProfileRequest {
  /** Required. */
  employeeId: string;
  bloodType?: string;
  allergies?: string;
  emergencyNotes?: string;
}

export interface UpdateEmployeeMedicalProfileRequest {
  bloodType?: string;
  allergies?: string;
  emergencyNotes?: string;
}
