/**
 * Statutory payroll templates — `/api/payroll/templates` (gated `MANAGE_TAX_BRACKETS`). A template is
 * shipped with the build (rates, brackets, cited sources); a tenant adopts it, sets its employer
 * settings and enables it, and payroll then computes that country's taxes and contributions from it.
 */

/** How a template's results were checked: against official test vectors, derived from the law, or not yet (draft). */
export type PayrollTemplateStatus = 'OFFICIAL_VECTORS' | 'LAW_DERIVED' | 'DRAFT';

export type TemplateInputType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'ENUM';

/** A setting (employer) or attribute (employee) the template reads. Values travel as strings. */
export interface TemplateInput {
  key: string;
  type: TemplateInputType;
  required: boolean;
  values: string[] | null;
  pattern: string | null;
  min: number | null;
  max: number | null;
  defaultValue: string | null;
  description: string | null;
}

export interface TemplateChangelogEntry {
  version: string;
  date: string;
  summary: string;
}

export interface TemplateSource {
  id: string;
  title: string;
  url: string | null;
  retrieved: string | null;
}

/**
 * How payroll pays one leave type in the template's country: the workspace's own rule, the template's
 * statutory rule (while it is on), or none — approved leave is then paid in full and the template doesn't see it.
 */
export interface TemplateLeaveCoverage {
  leaveType: 'SICK' | 'MATERNITY' | 'UNPAID';
  rule: 'WORKSPACE' | 'TEMPLATE' | 'NONE';
  /** What the statutory rule leaves out, for TEMPLATE. */
  note: string | null;
}

/** This tenant's adoption; `updateAvailable` when the shipped version differs from the one last reviewed. */
export interface TemplateAdoption {
  enabled: boolean;
  settings: Record<string, string>;
  acknowledgedVersion: string | null;
  updateAvailable: boolean;
}

/** `GET /api/payroll/templates[/{id}]`. */
export interface PayrollTemplate {
  id: string;
  name: string;
  version: string;
  status: PayrollTemplateStatus;
  country: string;
  subdivision: string | null;
  validFrom: string;
  validTo: string;
  currency: string;
  payFrequencies: string[];
  taxYearStart: string | null;
  /** False for drafts, which can't be enabled. */
  adoptable: boolean;
  changelog: TemplateChangelogEntry[];
  sources: TemplateSource[];
  employerInputs: TemplateInput[];
  employeeInputs: TemplateInput[];
  /** Tax types the template produces (tenant brackets of these types are ignored for covered employees). */
  ledgers: string[];
  limitations: string[];
  /** How many worked examples the template reproduces. */
  testCases: number;
  /** Sick, parental and unpaid leave in the template's country. */
  leavePay: TemplateLeaveCoverage[];
  /** Null if never adopted. */
  adoption: TemplateAdoption | null;
}

/** Body for `PUT /api/payroll/templates/{id}/adoption`. */
export interface ConfigurePayrollTemplateRequest {
  enabled: boolean;
  settings: Record<string, string>;
}

/** Result of `PUT .../adoption`: the template, and what in the tenant's setup interacts with it (only when enabling). */
export interface PayrollTemplateAdoptionResult {
  template: PayrollTemplate;
  warnings: string[];
}

/**
 * `GET /api/payroll/templates/{id}/employees` — an employee the template covers, with their profile.
 * `missing` lists required attributes the profile lacks; payroll can't calculate them until it's empty.
 */
export interface TemplateEmployee {
  employeeId: string;
  name: string;
  employeeNumber: string | null;
  jobTitle: string | null;
  department: string | null;
  status: string;
  attributes: Record<string, string>;
  missing: string[];
}

/** Body for `PATCH /api/payroll/templates/{id}/employees` — the same values for several employees; others kept. */
export interface UpdateTemplateProfilesRequest {
  employeeIds: string[];
  attributes: Record<string, string>;
}

/** `GET /api/payroll/templates/by-employee/{employeeId}` — a switched-on template that covers the employee, with their profile. */
export interface EmployeeTemplate {
  templateId: string;
  templateName: string;
  country: string;
  employee: TemplateEmployee;
}
