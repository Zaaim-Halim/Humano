import { AuditFields } from 'app/core/api';

/** WorkPermit — `/api/hr/work-permits`. */
export interface WorkPermit extends AuditFields {
  id: string;
  employeeId: string | null;
  visaType: string | null;
  permitNumber: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  sponsor: string | null;
  documentFileId: string | null;
}

export interface CreateWorkPermitRequest {
  /** Required. */
  employeeId: string;
  visaType?: string;
  permitNumber?: string;
  issueDate?: string;
  expiryDate?: string;
  sponsor?: string;
  documentFileId?: string;
}

export interface UpdateWorkPermitRequest {
  visaType?: string;
  permitNumber?: string;
  issueDate?: string;
  expiryDate?: string;
  sponsor?: string;
  documentFileId?: string;
}
