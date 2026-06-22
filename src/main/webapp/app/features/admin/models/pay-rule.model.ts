import { PayComponentCode } from './enums/pay-component-code.enum';

/** One callable formula function and the simple type names of its parameters. */
export interface FunctionMeta {
  name: string;
  parameterTypes: string[];
}

/**
 * Engine contract from `GET /api/payroll/pay-rules/formula-metadata`. Drives the
 * editor palette/autocomplete so the UI never hardcodes the function/variable list.
 */
export interface FormulaMetadata {
  functions: FunctionMeta[];
  variables: string[];
  constants: string[];
  dynamicVariablePattern: string;
  maxFormulaLength: number;
}

/** Summary of a pay rule attached to a component. */
export interface PayRuleSummary {
  id: string;
  formula: string;
  priority: number | null;
  active: boolean;
}

/** A pay component and its active rules (`GET /api/payroll/pay-components`). */
export interface PayComponent {
  id: string;
  code: PayComponentCode;
  name: string;
  kind: string;
  measure: string;
  taxable: boolean;
  contributesToSocial: boolean;
  percentage: boolean;
  calcPhase: number | null;
  ruleCount: number;
  activeRules: PayRuleSummary[];
}

/** Body for `POST /api/payroll/pay-rules`. */
export interface CreatePayRuleRequest {
  payComponentId: string;
  formula: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  priority?: number;
  baseFormulaRef?: string;
  active: boolean;
}

/** Result of `POST /api/payroll/pay-rules/validate-formula`. */
export interface FormulaValidationResult {
  formula: string;
  valid: boolean;
  testResult?: number;
  resultType?: string;
  error?: string;
}
