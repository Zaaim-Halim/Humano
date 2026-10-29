import { PayComponentCode } from '../models/enums/pay-component-code.enum';
import { PayComponent } from '../models/pay-rule.model';

/** A payroll template switched on (`GET /api/payroll/pay-rules/templates-in-use`): payroll uses it for employees in `country`. */
export interface TemplateInUse {
  id: string;
  name: string;
  country: string;
}

/**
 * How a rule on `component` works for employees a switched-on template covers, as the payroll run does
 * it: texts `humano.payRules.withTemplate.<key>`, in order.
 * <p>
 * Earnings, formulas included, are worked out first; the template taxes the taxable ones and charges
 * contributions on the social ones (every shipped template bases them on those), and some templates
 * read bonus or overtime pay apart. Formula deductions come off net pay after the template, so they
 * don't lower what it computes. Rules on income tax and employer charges don't run for its employees.
 * No template sees a formula's result before running, and no formula sees the template's.
 * Base salary comes from compensation, never from a formula, so there is nothing to say for it.
 */
export function ruleWithTemplate(component: Pick<PayComponent, 'code' | 'kind' | 'taxable' | 'contributesToSocial'>): string[] {
  const code = component.code;
  if (code === PayComponentCode.TAX_PIT || code === PayComponentCode.EMPLOYER_CHARGE) return [`replaced.${code}`];
  if (code === PayComponentCode.BASIC) return [];
  if (component.kind === 'EARNING') {
    return [
      'earning',
      component.taxable ? 'taxed' : 'untaxed',
      component.contributesToSocial ? 'social' : 'nonSocial',
      ...(code === PayComponentCode.BONUS || code === PayComponentCode.OT ? ['byCode'] : []),
      'variables',
    ];
  }
  return ['deduction', 'variables'];
}
