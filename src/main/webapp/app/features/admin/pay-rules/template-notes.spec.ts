import { ruleWithTemplate } from './template-notes';

type Component = Parameters<typeof ruleWithTemplate>[0];

const component = (code: string, kind: string, taxable = true, contributesToSocial = true): Component =>
  ({ code, kind, taxable, contributesToSocial }) as Component;

describe('ruleWithTemplate', () => {
  it('says a rule on income tax or employer charges does not apply to the template’s employees', () => {
    expect(ruleWithTemplate(component('TAX_PIT', 'DEDUCTION'))).toEqual(['replaced.TAX_PIT']);
    expect(ruleWithTemplate(component('EMPLOYER_CHARGE', 'EMPLOYER_CHARGE'))).toEqual(['replaced.EMPLOYER_CHARGE']);
  });

  it('says how the template taxes an earning, from its switches', () => {
    expect(ruleWithTemplate(component('ALLOWANCE', 'EARNING'))).toEqual(['earning', 'taxed', 'social', 'variables']);
    expect(ruleWithTemplate(component('ALLOWANCE', 'EARNING', false, false))).toEqual(['earning', 'untaxed', 'nonSocial', 'variables']);
  });

  it('warns that bonus and overtime pay can be treated apart from regular pay', () => {
    expect(ruleWithTemplate(component('BONUS', 'EARNING'))).toContain('byCode');
    expect(ruleWithTemplate(component('OT', 'EARNING'))).toContain('byCode');
    expect(ruleWithTemplate(component('ALLOWANCE', 'EARNING'))).not.toContain('byCode');
  });

  it('says a deduction comes off net pay after the template', () => {
    expect(ruleWithTemplate(component('DEDUCTION', 'DEDUCTION'))).toEqual(['deduction', 'variables']);
  });

  it('says nothing for base salary, which no formula calculates', () => {
    expect(ruleWithTemplate(component('BASIC', 'EARNING'))).toEqual([]);
  });
});
