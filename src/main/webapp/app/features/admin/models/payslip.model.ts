import { PayrollResult } from './payroll-result.model';

/** `GET /api/payroll/payslips/{id}` and related lookups. */
export interface Payslip {
  id: string;
  number: string;
  employeeId: string;
  employeeName: string | null;
  employeeCode: string | null;
  department: string | null;
  position: string | null;
  resultId: string | null;
  periodId: string | null;
  periodCode: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  paymentDate: string | null;
  gross: number;
  totalDeductions: number;
  net: number;
  currencyCode: string | null;
  pdfUrl: string | null;
  /** Full computed breakdown (line items). */
  details: PayrollResult | null;
}

/** Criteria for `POST /api/payroll/payslips/search`. */
export interface PayslipSearchRequest {
  employeeId?: string;
  payslipNumber?: string;
  payrollRunId?: string;
  minGross?: number;
  maxGross?: number;
  minNet?: number;
  maxNet?: number;
  periodStartFrom?: string;
  periodStartTo?: string;
  periodEndFrom?: string;
  periodEndTo?: string;
  createdBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
}
