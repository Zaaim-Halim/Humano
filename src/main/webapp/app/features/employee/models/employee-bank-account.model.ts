import { AuditFields } from 'app/core/api';

/** EmployeeBankAccount — `/api/hr/employee-bank-accounts`. */
export interface EmployeeBankAccount extends AuditFields {
  id: string;
  employeeId: string | null;
  bankName: string | null;
  iban: string | null;
  swift: string | null;
  accountHolder: string | null;
  currency: string | null;
  primary: boolean;
}

export interface CreateEmployeeBankAccountRequest {
  /** Required. */
  employeeId: string;
  bankName?: string;
  iban?: string;
  swift?: string;
  accountHolder?: string;
  currency?: string;
  primary?: boolean;
}

export interface UpdateEmployeeBankAccountRequest {
  bankName?: string;
  iban?: string;
  swift?: string;
  accountHolder?: string;
  currency?: string;
  primary?: boolean;
}
