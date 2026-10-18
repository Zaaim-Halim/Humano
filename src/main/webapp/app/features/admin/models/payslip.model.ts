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

/** Year-to-date pay — `…/ytd?year=&through=`: the payslips of the year whose period ends by `through`. */
export interface PayslipYtd {
  year: number;
  /** ISO date the totals run to. */
  through: string;
  payslipCount: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  /** Net pay by month (`yyyy-MM`), in order. */
  monthlyNet: Record<string, number>;
}

/** Criteria for `POST /api/payroll/payslips/search`. */
export interface PayslipSearchRequest {
  employeeId?: string;
  payslipNumber?: string;
  payrollRunId?: string;
  /** Free text: employee first/last name or payslip number. */
  query?: string;
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
