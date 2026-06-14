import { PayrollScope } from './enums/payroll-scope.enum';
import { RunStatus } from './enums/run-status.enum';

export interface PayrollValidationError {
  employeeId: string;
  employeeName: string | null;
  errorCode: string;
  message: string;
  severity: string;
}

/** A payroll run — returned by initiate/calculate/approve/post/recalculate. */
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
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  totalEmployerCost: number;
  currencyCode: string | null;
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

/** `GET /api/payroll/runs/{id}/summary`. */
export interface PayrollRunSummary {
  runId: string;
  periodCode: string | null;
  totalEmployees: number;
  processedEmployees: number;
  errorCount: number;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  totalEmployerCost: number;
  totalPayrollCost: number;
  currencyCode: string | null;
  earningsByComponent: Record<string, number>;
  deductionsByComponent: Record<string, number>;
  byDepartment: Record<string, DepartmentPayrollSummary>;
  topEarners: EmployeePayrollSummary[];
  comparison: ComparisonWithPreviousPeriod | null;
}

export interface DepartmentPayrollSummary {
  departmentName: string;
  employeeCount: number;
  totalGross: number;
  totalNet: number;
  averageSalary: number;
}

export interface EmployeePayrollSummary {
  employeeId: string;
  employeeName: string | null;
  department: string | null;
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
