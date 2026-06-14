import { Basis } from './enums/basis.enum';

/** `GET` compensation rows (by department/search/history). */
export interface Compensation {
  id: string;
  employeeId: string;
  employeeName: string | null;
  currencyId: string | null;
  currencyCode: string | null;
  baseAmount: number;
  basis: Basis;
  effectiveFrom: string;
  effectiveTo: string | null;
  active: boolean;
}

export interface CreateCompensationRequest {
  employeeId: string;
  positionId: string;
  currencyId: string;
  baseAmount: number;
  basis: Basis;
  effectiveFrom: string;
  effectiveTo?: string;
}

/**
 * `POST /api/payroll/compensations/adjust`. Provide exactly one of `newAmount`
 * or `adjustmentPercentage` (the backend rejects both/neither).
 */
export interface SalaryAdjustmentRequest {
  employeeId: string;
  newAmount?: number;
  adjustmentPercentage?: number;
  newBasis?: Basis;
  newCurrencyId?: string;
  effectiveFrom: string;
  reason: string;
}

export interface SalaryChange {
  compensationId: string;
  previousAmount: number | null;
  newAmount: number;
  basis: Basis;
  currencyCode: string | null;
  changeAmount: number | null;
  changePercentage: number | null;
  effectiveFrom: string;
  reason: string | null;
  changedBy: string | null;
}

/** `GET /api/payroll/compensations/employees/{employeeId}/history`. */
export interface SalaryHistory {
  employeeId: string;
  employeeName: string | null;
  history: SalaryChange[];
  totalGrowthPercentage: number | null;
  averageAnnualGrowth: number | null;
  currentCompensation: Compensation | null;
}

/** Criteria for `POST /api/payroll/compensations/search`. */
export interface CompensationSearchRequest {
  employeeId?: string;
  positionId?: string;
  currencyId?: string;
  basis?: Basis;
  minAmount?: number;
  maxAmount?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  activeOnly?: boolean;
  createdBy?: string;
  createdDateFrom?: string;
  createdDateTo?: string;
}
