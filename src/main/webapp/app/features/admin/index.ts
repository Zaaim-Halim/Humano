// Admin persona — org structure, payroll, and user administration.

// Enums
export { OrganizationalUnitType } from './models/enums/organizational-unit-type.enum';
export { RunStatus } from './models/enums/run-status.enum';
export { PayrollScope } from './models/enums/payroll-scope.enum';
export { Basis } from './models/enums/basis.enum';

// Org structure
export { DepartmentService } from './services/department.service';
export type { Department, CreateDepartmentRequest, UpdateDepartmentRequest } from './models/department.model';

export { PositionService } from './services/position.service';
export type { Position, CreatePositionRequest, UpdatePositionRequest } from './models/position.model';

export { OrganizationalUnitService } from './services/organizational-unit.service';
export type {
  OrganizationalUnit,
  CreateOrganizationalUnitRequest,
  UpdateOrganizationalUnitRequest,
} from './models/organizational-unit.model';

// Payroll
export { PayrollRunService } from './services/payroll-run.service';
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
} from './models/payroll-run.model';

export { PayrollResultService } from './services/payroll-result.service';
export type { PayrollResult, PayrollLineItem } from './models/payroll-result.model';

export { PayslipService } from './services/payslip.service';
export type { Payslip, PayslipSearchRequest } from './models/payslip.model';

export { CompensationService } from './services/compensation.service';
export type {
  Compensation,
  CreateCompensationRequest,
  SalaryAdjustmentRequest,
  SalaryChange,
  SalaryHistory,
  CompensationSearchRequest,
} from './models/compensation.model';

export { PayrollPeriodService } from './services/payroll-period.service';
export type { PayrollPeriod, GeneratePayrollPeriodsRequest } from './models/payroll-period.model';

export { CurrencyService } from './services/currency.service';
export type { Currency } from './models/currency.model';

export { PayrollCalendarService } from './services/payroll-calendar.service';
export type { PayrollCalendar, PayrollPeriodSummary } from './models/payroll-calendar.model';

export { OrganizationSettingsService } from './services/org-settings.service';
export type { OrganizationSettings, UpdateOrganizationSettingsRequest } from './models/org-settings.model';

// User administration
export { AdminUserService } from './services/admin-user.service';
export type { ManagedUserPage } from './services/admin-user.service';
export type { ManagedUser, CreateUserRequest, UpdateUserRequest } from './models/managed-user.model';
