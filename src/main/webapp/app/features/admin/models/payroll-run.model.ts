import { PayrollScope } from './enums/payroll-scope.enum';
import { RunStatus } from './enums/run-status.enum';

export interface PayrollValidationError {
  employeeId: string;
  employeeName: string | null;
  errorCode: string;
  message: string;
  severity: string;
  /** Set when the employee lacks details for this payroll template (`TEMPLATE_PROFILE_INCOMPLETE`): where to fill them in. */
  templateId: string | null;
}

/** A run's totals in one currency (backend `PayrollCurrencyTotals`). */
export interface PayrollCurrencyTotals {
  currencyCode: string;
  employeeCount: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  totalEmployerCost: number;
}

/**
 * A payroll run — returned by initiate/calculate/approve/post/recalculate. The single totals and
 * `currencyCode` are null when employees are paid in several currencies and the run has no reporting
 * currency; `totalsByCurrency` always has one entry per currency.
 */
export interface PayrollRun {
  id: string;
  periodId: string;
  periodCode: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  paymentDate: string | null;
  scope: string | null;
  status: RunStatus;
  employeeCount: number;
  totalGross: number | null;
  totalDeductions: number | null;
  totalNet: number | null;
  totalEmployerCost: number | null;
  currencyCode: string | null;
  totalsByCurrency: PayrollCurrencyTotals[];
  approvedAt: string | null;
  approvedBy: string | null;
  validationErrors: PayrollValidationError[];
  createdAt: string | null;
  createdBy: string | null;
}

export interface InitiatePayrollRunRequest {
  periodId: string;
  scope?: PayrollScope;
  excludedEmployeeIds?: string[];
  draftMode?: boolean;
  notes?: string;
  reportingCurrencyId?: string;
}

export interface ApprovePayrollRunRequest {
  payrollRunId: string;
  approverId: string;
  approvalNotes?: string;
  forceApproval?: boolean;
}

export interface RecalculatePayrollRequest {
  payrollRunId: string;
  employeeIds?: string[];
  recalculateAll?: boolean;
  componentsToRecalculate?: string[];
  reason?: string;
}

/**
 * `GET /api/payroll/runs/{id}/summary`. In a run paid in several currencies without a reporting
 * currency the single totals, `currencyCode` and `comparison` are null and departments are split by
 * currency; `earnings` / `deductions` carry each component's currency.
 */
export interface PayrollRunSummary {
  runId: string;
  periodCode: string | null;
  totalEmployees: number;
  processedEmployees: number;
  errorCount: number;
  totalGross: number | null;
  totalDeductions: number | null;
  totalNet: number | null;
  totalEmployerCost: number | null;
  totalPayrollCost: number | null;
  currencyCode: string | null;
  totalsByCurrency: PayrollCurrencyTotals[];
  /** Filled only when every line is in `currencyCode`; prefer `earnings`. */
  earningsByComponent: Record<string, number>;
  /** Filled only when every line is in `currencyCode`; prefer `deductions`. */
  deductionsByComponent: Record<string, number>;
  earnings: ComponentTotal[];
  deductions: ComponentTotal[];
  byDepartment: Record<string, DepartmentPayrollSummary>;
  topEarners: EmployeePayrollSummary[];
  comparison: ComparisonWithPreviousPeriod | null;
}

/** One pay component's total in one currency. */
export interface ComponentTotal {
  name: string;
  currencyCode: string;
  amount: number;
}

export interface DepartmentPayrollSummary {
  departmentName: string;
  currencyCode: string | null;
  employeeCount: number;
  totalGross: number;
  totalNet: number;
  averageSalary: number;
}

export interface EmployeePayrollSummary {
  employeeId: string;
  employeeName: string | null;
  department: string | null;
  currencyCode: string | null;
  gross: number;
  net: number;
}

export interface ComparisonWithPreviousPeriod {
  previousGross: number;
  grossChange: number;
  grossChangePercentage: number;
  previousNet: number;
  netChange: number;
  netChangePercentage: number;
  previousEmployeeCount: number;
  employeeCountChange: number;
}
