/** A single component line on a payroll result/payslip. */
export interface PayrollLineItem {
  id: string;
  componentCode: string;
  componentName: string;
  quantity: number | null;
  rate: number | null;
  amount: number;
  sequence: number;
  explanation: string | null;
}

/** `GET /api/payroll/results/{id}` — one employee's computed payroll for a run. */
export interface PayrollResult {
  id: string;
  employeeId: string;
  employeeName: string | null;
  employeeCode: string | null;
  runId: string;
  periodId: string;
  periodCode: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  paymentDate: string | null;
  gross: number;
  totalDeductions: number;
  net: number;
  employerCost: number;
  currencyCode: string | null;
  earnings: PayrollLineItem[];
  deductions: PayrollLineItem[];
  employerCharges: PayrollLineItem[];
  payslipNumber: string | null;
  payslipUrl: string | null;
}
