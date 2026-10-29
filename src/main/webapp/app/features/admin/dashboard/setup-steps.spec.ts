import { CompanyProfile } from 'app/core/company/company.service';

import { PayrollTemplate } from '../models/payroll-template.model';
import { nlTemplate } from '../payroll-templates/template-fixtures.spec-helper';
import { SetupState, SetupStep, setupSteps } from './setup-steps';

const profile = (extra: Partial<CompanyProfile> = {}): CompanyProfile => ({
  name: 'Bakkerij de Vries',
  logo: null,
  timezone: 'Europe/Amsterdam',
  defaultCurrencyId: null,
  currencyCode: null,
  standardHoursPerDay: 8,
  standardHoursPerWeek: 40,
  standardMonthlyHours: 173,
  overtimeMultiplier: 1.5,
  defaultBasis: 'MONTHLY',
  ...extra,
});

/** The Netherlands template as sign-up adds it: adopted, switched off. */
const chosenAtSignUp = (enabled = false): PayrollTemplate =>
  nlTemplate({ adoption: { enabled, settings: {}, acknowledgedVersion: '2026.1.0', updateAvailable: false } });

const state = (extra: Partial<SetupState> = {}): SetupState => ({
  profile: profile(),
  employees: 0,
  runs: 0,
  templates: [nlTemplate()],
  planChosen: false,
  ...extra,
});

const step = (steps: SetupStep[], id: SetupStep['id']): SetupStep | undefined => steps.find(s => s.id === id);

describe('setupSteps', () => {
  it('leads to the template chosen at sign-up, before the first payroll run', () => {
    const steps = setupSteps(state({ templates: [nlTemplate(), chosenAtSignUp()] }));

    expect(steps.map(s => s.id)).toEqual(['company', 'people', 'template', 'payroll', 'plan']);
    expect(step(steps, 'template')).toEqual({
      id: 'template',
      icon: 'landmark',
      link: '/payroll/templates/nl-employment',
      done: false,
      params: { country: 'Netherlands' },
    });
  });

  it('ticks the template step once the template is switched on', () => {
    const steps = setupSteps(state({ templates: [chosenAtSignUp(true)] }));

    expect(step(steps, 'template')?.done).toBe(true);
  });

  it('has no template step when none was chosen', () => {
    expect(step(setupSteps(state()), 'template')).toBeUndefined();
    expect(step(setupSteps(state({ templates: null })), 'template')).toBeUndefined();
  });

  it('keeps the payroll step open while the chosen template is off, even after a run', () => {
    expect(step(setupSteps(state({ runs: 1, templates: [chosenAtSignUp()] })), 'payroll')?.done).toBe(false);
    expect(step(setupSteps(state({ runs: 1, templates: [chosenAtSignUp(true)] })), 'payroll')?.done).toBe(true);
    expect(step(setupSteps(state({ runs: 1 })), 'payroll')?.done).toBe(true);
  });

  it("doesn't count the currency a chosen template set as a completed company profile", () => {
    const withCurrency = profile({ defaultCurrencyId: 'eur' });

    expect(step(setupSteps(state({ profile: withCurrency, templates: [chosenAtSignUp()] })), 'company')?.done).toBe(false);
    expect(
      step(setupSteps(state({ profile: { ...withCurrency, logo: 'logo.png' }, templates: [chosenAtSignUp()] })), 'company')?.done,
    ).toBe(true);
    expect(step(setupSteps(state({ profile: withCurrency })), 'company')?.done).toBe(true);
  });

  it('leaves out the payroll step without access to payroll', () => {
    expect(step(setupSteps(state({ runs: null })), 'payroll')).toBeUndefined();
  });
});
