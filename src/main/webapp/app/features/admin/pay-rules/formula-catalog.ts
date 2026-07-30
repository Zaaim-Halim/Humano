/**
 * Front-end enrichment for the pay-rule formula editor.
 *
 * The BACKEND (`GET /api/payroll/pay-rules/formula-metadata`) stays the single
 * source of truth for WHICH functions / variables / constants the engine
 * accepts. This catalog only layers human-readable names, one-line
 * descriptions, categories and worked examples on top of the ones we know
 * about, so the editor can present a guided, documented reference instead of a
 * wall of bare tokens. Anything the engine reports that isn't described here
 * still appears — it just falls back to a minimal entry (name + raw signature).
 *
 * Descriptions live here (not i18n) on purpose: they are technical domain
 * reference tied 1:1 to `PayrollFormulaEngine.Functions`, and keeping them
 * beside the engine contract makes them easy to keep in sync. UI chrome
 * (labels, buttons, headings) is translated normally via `payRules.json`.
 */

export type FunctionCategory = 'math' | 'logical' | 'threshold' | 'band' | 'date';

export interface FunctionDoc {
  category: FunctionCategory;
  /** Human parameter names, e.g. ['value', 'percent'] — nicer than raw Java types. */
  params: string[];
  returns: string;
  description: string;
  example?: string;
}

export const FUNCTION_CATEGORY_ORDER: FunctionCategory[] = ['math', 'logical', 'threshold', 'band', 'date'];

export const FUNCTION_CATEGORY_LABEL: Record<FunctionCategory, string> = {
  math: 'Math',
  logical: 'Logical',
  threshold: 'Caps & thresholds',
  band: 'Progressive bands',
  date: 'Dates',
};

// Icons must come from the curated registry in `config/lucide-icons.ts`.
export const FUNCTION_CATEGORY_ICON: Record<FunctionCategory, string> = {
  math: 'function-square',
  logical: 'git-fork',
  threshold: 'trending-up',
  band: 'layers',
  date: 'calendar',
};

/** Keyed by the engine's function name. */
export const FUNCTION_DOCS: Record<string, FunctionDoc> = {
  // ----- Math -----
  min: {
    category: 'math',
    params: ['a', 'b'],
    returns: 'number',
    description: 'The smaller of two values.',
    example: '#min(#grossSalary, 176100)',
  },
  max: {
    category: 'math',
    params: ['a', 'b'],
    returns: 'number',
    description: 'The larger of two values.',
    example: '#max(0, #grossSalary - 12570)',
  },
  abs: { category: 'math', params: ['value'], returns: 'number', description: 'Absolute (positive) value.' },
  clamp: {
    category: 'math',
    params: ['value', 'low', 'high'],
    returns: 'number',
    description: 'Keep a value within a low–high range.',
    example: '#clamp(#bonus, 0, 5000)',
  },
  round: {
    category: 'math',
    params: ['value', 'decimals'],
    returns: 'number',
    description: 'Round to N decimals (half-up).',
    example: '#round(#netPay, 2)',
  },
  roundUp: { category: 'math', params: ['value', 'decimals'], returns: 'number', description: 'Always round up to N decimals.' },
  roundDown: { category: 'math', params: ['value', 'decimals'], returns: 'number', description: 'Always round down to N decimals.' },
  ceil: { category: 'math', params: ['value'], returns: 'number', description: 'Round up to a whole number.' },
  floor: { category: 'math', params: ['value'], returns: 'number', description: 'Round down to a whole number.' },
  roundToIncrement: {
    category: 'math',
    params: ['value', 'increment'],
    returns: 'number',
    description: 'Round to the nearest multiple, e.g. 0.05 for Swiss centimes.',
    example: '#roundToIncrement(#netPay, 0.05)',
  },
  pct: {
    category: 'math',
    params: ['value', 'percent'],
    returns: 'number',
    description: 'A percentage of a value (value × percent ÷ 100).',
    example: '#pct(#grossSalary, 6.2)',
  },

  // ----- Logical -----
  iif: {
    category: 'logical',
    params: ['condition', 'ifTrue', 'ifFalse'],
    returns: 'number',
    description: 'Pick one of two values based on a condition — like a ? b : c.',
    example: '#iif(#employeeDependents > 0, 200, 0)',
  },

  // ----- Caps & thresholds -----
  cap: {
    category: 'threshold',
    params: ['value', 'ceiling'],
    returns: 'number',
    description: 'Cap a value so it never exceeds a ceiling.',
    example: '#cap(#pct(#grossSalary, 25), 24 * #MINIMUM_WAGE)',
  },
  threshold: {
    category: 'threshold',
    params: ['value', 'floor'],
    returns: 'number',
    description: 'The amount above a floor: max(0, value − floor).',
    example: '#threshold(#grossSalary, #TAX_FREE_ALLOWANCE)',
  },

  // ----- Progressive bands -----
  band: {
    category: 'band',
    params: ['value', 'bands'],
    returns: 'number',
    description: 'Progressive brackets. bands is a list of {low, high, rate}.',
    example: '#band(#max(0, #grossSalary - 12570), {{0, 37700, 0.20}, {37700, 112430, 0.40}})',
  },

  // ----- Dates -----
  yearsBetween: {
    category: 'date',
    params: ['from', 'to'],
    returns: 'whole years',
    description: 'Whole years between two dates.',
    example: '#yearsBetween(#employeeHireDate, #periodEndDate)',
  },
  monthsBetween: { category: 'date', params: ['from', 'to'], returns: 'whole months', description: 'Whole months between two dates.' },
  daysBetween: { category: 'date', params: ['from', 'to'], returns: 'whole days', description: 'Whole days between two dates.' },
};

export interface VariableGroupDoc {
  id: string;
  label: string;
}

export const VARIABLE_GROUP_ORDER: VariableGroupDoc[] = [
  { id: 'earnings', label: 'Earnings & totals' },
  { id: 'period', label: 'Pay period' },
  { id: 'employee', label: 'Employee' },
  { id: 'policy', label: 'Company policy' },
  { id: 'country', label: 'Country inputs' },
  { id: 'other', label: 'Other' },
];

interface VariableDoc {
  group: string;
  description: string;
}

/** Keyed by the engine's variable name. Unlisted names fall into the "Other" group. */
export const VARIABLE_DOCS: Record<string, VariableDoc> = {
  // Earnings & totals
  grossSalary: { group: 'earnings', description: 'Gross pay before deductions.' },
  gross: { group: 'earnings', description: 'Alias of grossSalary.' },
  baseSalary: { group: 'earnings', description: 'Contracted base salary.' },
  taxableIncome: { group: 'earnings', description: 'Income subject to tax.' },
  preTaxDeductions: { group: 'earnings', description: 'Total pre-tax deductions.' },
  postTaxDeductions: { group: 'earnings', description: 'Total post-tax deductions.' },
  netPay: { group: 'earnings', description: 'Take-home pay after deductions.' },
  employerCost: { group: 'earnings', description: 'Total cost to the employer.' },
  employeeId: { group: 'earnings', description: 'Identifier of the employee being paid.' },
  // Period
  periodStartDate: { group: 'period', description: 'First day of the pay period.' },
  periodEndDate: { group: 'period', description: 'Last day of the pay period.' },
  paymentDate: { group: 'period', description: 'Date wages are paid.' },
  workDays: { group: 'period', description: 'Working days in the period.' },
  periodYear: { group: 'period', description: 'Calendar year of the period.' },
  periodMonth: { group: 'period', description: 'Month number (1–12) of the period.' },
  // Employee
  employeeCountry: { group: 'employee', description: 'ISO country of the employee.' },
  employeeBirthDate: { group: 'employee', description: 'Date of birth.' },
  employeeHireDate: { group: 'employee', description: 'Hire date.' },
  employeeAge: { group: 'employee', description: 'Age in whole years.' },
  employeeYearsOfService: { group: 'employee', description: 'Completed years of service.' },
  employeeMaritalStatus: { group: 'employee', description: "Marital status, e.g. 'MARRIED'." },
  employeeDependents: { group: 'employee', description: 'Number of dependents.' },
  currencyCode: { group: 'employee', description: 'Payroll currency, e.g. EUR.' },
  // Company policy
  standardHoursPerDay: { group: 'policy', description: 'Standard working hours per day.' },
  standardHoursPerWeek: { group: 'policy', description: 'Standard working hours per week.' },
  standardMonthlyHours: { group: 'policy', description: 'Standard monthly hours.' },
  overtimeMultiplier: { group: 'policy', description: 'Default overtime rate multiplier.' },
  // Country inputs
  MINIMUM_WAGE: { group: 'country', description: 'Statutory minimum wage (if configured).' },
  TAX_FREE_ALLOWANCE: { group: 'country', description: 'Tax-free allowance (if configured).' },
  SOCIAL_SECURITY_CAP: { group: 'country', description: 'Social-security contribution ceiling.' },
};

/** Keyed by the engine's constant name → its value + one-liner. */
export const CONSTANT_DOCS: Record<string, string> = {
  MONTHS_IN_YEAR: '12 — months in a year.',
  WEEKS_IN_YEAR: '52 — weeks in a year.',
  DAYS_IN_YEAR: '365 — days in a year.',
  HOURS_IN_MONTH: '160 — default working hours per month.',
  WORKDAYS_IN_MONTH: '22 — default working days per month.',
};

/**
 * SpEL operators the engine evaluates natively (context-independent, always
 * available). `insert` is the raw text dropped into the formula; `caretBack`
 * leaves the cursor N chars from the end (e.g. inside `()`).
 */
export interface OperatorItem {
  group: string;
  symbol: string;
  insert: string;
  caretBack: number;
  description: string;
}

export const OPERATOR_GROUP_ORDER: { id: string; label: string }[] = [
  { id: 'arithmetic', label: 'Arithmetic' },
  { id: 'comparison', label: 'Comparison' },
  { id: 'logical', label: 'Logical' },
  { id: 'grouping', label: 'Grouping' },
];

export const OPERATORS: OperatorItem[] = [
  { group: 'arithmetic', symbol: '+', insert: '+ ', caretBack: 0, description: 'Add.' },
  { group: 'arithmetic', symbol: '−', insert: '- ', caretBack: 0, description: 'Subtract.' },
  { group: 'arithmetic', symbol: '×', insert: '* ', caretBack: 0, description: 'Multiply.' },
  { group: 'arithmetic', symbol: '÷', insert: '/ ', caretBack: 0, description: 'Divide.' },
  { group: 'arithmetic', symbol: '%', insert: '% ', caretBack: 0, description: 'Remainder (modulo).' },
  { group: 'comparison', symbol: '>', insert: '> ', caretBack: 0, description: 'Greater than.' },
  { group: 'comparison', symbol: '≥', insert: '>= ', caretBack: 0, description: 'Greater than or equal.' },
  { group: 'comparison', symbol: '<', insert: '< ', caretBack: 0, description: 'Less than.' },
  { group: 'comparison', symbol: '≤', insert: '<= ', caretBack: 0, description: 'Less than or equal.' },
  { group: 'comparison', symbol: '==', insert: '== ', caretBack: 0, description: 'Equal to.' },
  { group: 'comparison', symbol: '≠', insert: '!= ', caretBack: 0, description: 'Not equal to.' },
  { group: 'logical', symbol: 'and', insert: 'and ', caretBack: 0, description: 'Both conditions are true.' },
  { group: 'logical', symbol: 'or', insert: 'or ', caretBack: 0, description: 'Either condition is true.' },
  { group: 'logical', symbol: 'not', insert: 'not ', caretBack: 0, description: 'Negate a condition.' },
  { group: 'logical', symbol: '? :', insert: '? : ', caretBack: 2, description: 'If/else — condition ? ifTrue : ifFalse.' },
  { group: 'grouping', symbol: '( )', insert: '()', caretBack: 1, description: 'Group to control precedence.' },
  { group: 'grouping', symbol: '{ }', insert: '{}', caretBack: 1, description: 'List literal, e.g. band brackets.' },
];

export interface Recipe {
  title: string;
  description: string;
  formula: string;
}

/** Real-world starting points, lifted from the engine's documented recipes. */
export const RECIPES: Recipe[] = [
  {
    title: 'US Social Security (6.2%, capped)',
    description: '6.2% of wages up to the annual cap.',
    formula: '#pct(#min(#grossSalary, 176100), 6.2)',
  },
  {
    title: 'Romania CAS (25%, capped)',
    description: '25% of gross, capped at 24× the minimum wage.',
    formula: '#cap(#pct(#grossSalary, 25), 24 * #MINIMUM_WAGE)',
  },
  {
    title: 'UK PAYE (progressive bands)',
    description: 'Banded income tax above the personal allowance.',
    formula: '#band(#max(0, #grossSalary - 12570), {{0, 37700, 0.20}, {37700, 112430, 0.40}, {112430, 9999999, 0.45}})',
  },
  {
    title: 'Seniority bonus (5%/yr, cap 30%)',
    description: '5% of base salary per year of service, up to 30%.',
    formula: '#pct(#baseSalary, #min(5 * #employeeYearsOfService, 30))',
  },
  {
    title: 'Swiss rounding (nearest 0.05)',
    description: 'Round net pay to the nearest 5 centimes.',
    formula: '#roundToIncrement(#netPay, 0.05)',
  },
  {
    title: 'Conditional rate (marital status)',
    description: 'Lower rate for married employees with dependents.',
    formula: "(#employeeMaritalStatus == 'MARRIED' and #employeeDependents > 0) ? #pct(#taxableIncome, 22) : #pct(#taxableIncome, 25)",
  },
];
