import { PayrollValidationError } from '../models/payroll-run.model';
import { groupIssues } from './issue-groups';

const issue = (
  employeeId: string,
  message: string,
  errorCode = 'CALCULATION_ERROR',
  templateId: string | null = null,
): PayrollValidationError => ({
  employeeId,
  employeeName: employeeId.toUpperCase(),
  errorCode,
  message,
  severity: 'ERROR',
  templateId,
});

describe('groupIssues', () => {
  it('says a shared reason once, with its employees in the run order, largest group first', () => {
    const groups = groupIssues([issue('a', 'Rare'), issue('b', 'Common'), issue('c', 'Common')]);

    expect(groups.map(g => [g.message, g.people.map(p => p.name)])).toEqual([
      ['Common', ['B', 'C']],
      ['Rare', ['A']],
    ]);
  });

  it('shows the working days of a leave warning with the employee, not in the reason', () => {
    const groups = groupIssues([
      issue('a', 'Sick leave paid in full (2 working days): no leave pay rule for sick leave in Estonia.', 'LEAVE_WITHOUT_RULE'),
      issue('b', 'Sick leave paid in full (1 working day): no leave pay rule for sick leave in Estonia.', 'LEAVE_WITHOUT_RULE'),
    ]);

    expect(groups).toHaveLength(1);
    expect(groups[0].message).toBe('Sick leave paid in full: no leave pay rule for sick leave in Estonia.');
    expect(groups[0].people.map(p => p.detail)).toEqual(['2 working days', '1 working day']);
  });

  it('keeps missing details for different templates apart', () => {
    const groups = groupIssues([
      issue('a', 'Missing required employee attributes', 'TEMPLATE_PROFILE_INCOMPLETE', 'nl-employment'),
      issue('b', 'Missing required employee attributes', 'TEMPLATE_PROFILE_INCOMPLETE', 'de-employment'),
    ]);

    expect(groups.map(g => g.templateId)).toEqual(['nl-employment', 'de-employment']);
  });
});
