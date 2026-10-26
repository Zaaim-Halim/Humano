import { LeaveType } from 'app/features/employee/models/enums/leave-type.enum';

/**
 * A stretch of an absence deducted at its own rate (backend `LeavePayBand`). Days count in calendar
 * days from the absence's first day; `toDay` null = to the end of the absence.
 */
export interface LeavePayBand {
  fromDay: number;
  toDay: number | null;
  /** Share of the daily salary not paid, 0–100. */
  deductionPercentage: number;
}

/** `GET /api/payroll/leave-pay-rules` row (backend `LeaveTypeRuleResponse`). */
export interface LeavePayRule {
  /** null for a payroll template's statutory rule. */
  id: string | null;
  countryId: string;
  /** ISO 3166-1 alpha-2. */
  countryCode: string | null;
  countryName: string;
  leaveType: LeaveType;
  /** Deduction on days no band covers, 0–100. */
  deductionPercentage: number;
  payBands: LeavePayBand[];
  /** Pre-tax: the deduction lowers taxable pay and contribution bases. */
  affectsTaxableSalary: boolean;
  description: string;
  /** WORKSPACE: the workspace's own rule; TEMPLATE: an enabled payroll template's statutory rule. */
  origin: 'WORKSPACE' | 'TEMPLATE';
  templateId: string | null;
  templateName: string | null;
  templateVersion: string | null;
  /** What a statutory rule leaves out. */
  note: string | null;
  sourceTitle: string | null;
  sourceUrl: string | null;
  /** A workspace rule replacing a template's statutory rule for the same country and leave type. */
  overridesTemplate: boolean;
}

/** Body for `POST /api/payroll/leave-pay-rules`. */
export interface CreateLeavePayRuleRequest {
  countryId: string;
  leaveType: LeaveType;
  deductionPercentage: number;
  payBands: LeavePayBand[];
  affectsTaxableSalary: boolean;
}

/** Body for `PUT /api/payroll/leave-pay-rules/{id}`. */
export type UpdateLeavePayRuleRequest = Pick<CreateLeavePayRuleRequest, 'deductionPercentage' | 'payBands' | 'affectsTaxableSalary'>;
