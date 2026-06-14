// Enums
export { RunStatus } from './enums/run-status.enum';
export { PayrollScope } from './enums/payroll-scope.enum';
export { Basis } from './enums/basis.enum';

// Payroll (Phase 4.3)
export { PayrollRunService } from './payroll-run.service';
export type {
  PayrollRun,
  PayrollValidationError,
  PayrollRunSummary,
  DepartmentPayrollSummary,
  EmployeePayrollSummary,
  ComparisonWithPreviousPeriod,
  InitiatePayrollRunRequest,
  ApprovePayrollRunRequest,
  RecalculatePayrollRequest,
} from './payroll-run.model';

export { PayrollResultService } from './payroll-result.service';
export type { PayrollResult, PayrollLineItem } from './payroll-result.model';

export { PayslipService } from './payslip.service';
export type { Payslip, PayslipSearchRequest } from './payslip.model';

export { CompensationService } from './compensation.service';
export type {
  Compensation,
  CreateCompensationRequest,
  SalaryAdjustmentRequest,
  SalaryChange,
  SalaryHistory,
  CompensationSearchRequest,
} from './compensation.model';

export { PayrollPeriodService } from './payroll-period.service';
export type { PayrollPeriod, GeneratePayrollPeriodsRequest } from './payroll-period.model';
