import { PayrollValidationError } from '../models/payroll-run.model';

/** One employee under a reason: their name, and what applies to them alone (e.g. "2 working days"). */
export interface IssuePerson {
  employeeId: string;
  name: string;
  detail: string | null;
}

/** Employees a run found the same thing about, so the reason is said once and the people listed under it. */
export interface IssueGroup {
  key: string;
  message: string;
  /** Where to fill in missing template details (`TEMPLATE_PROFILE_INCOMPLETE`); null otherwise. */
  templateId: string | null;
  people: IssuePerson[];
}

/**
 * The per-employee part of a leave warning, "(N working days)" in PayrollProcessingService#leaveWithoutRule:
 * taken out of the reason and shown with the employee instead.
 */
const WORKING_DAYS = / \((\d+ working days?)\)/;

/** Groups a run's issues by reason, largest group first; within a group, employees keep the run's order. */
export function groupIssues(issues: PayrollValidationError[]): IssueGroup[] {
  const groups = new Map<string, IssueGroup>();
  for (const issue of issues) {
    const days = WORKING_DAYS.exec(issue.message);
    const message = days ? issue.message.replace(WORKING_DAYS, '') : issue.message;
    const key = `${issue.errorCode}|${issue.templateId ?? ''}|${message}`;
    let group = groups.get(key);
    if (!group) {
      group = { key, message, templateId: issue.templateId, people: [] };
      groups.set(key, group);
    }
    group.people.push({ employeeId: issue.employeeId, name: issue.employeeName ?? issue.employeeId, detail: days ? days[1] : null });
  }
  return [...groups.values()].sort((a, b) => b.people.length - a.people.length);
}
