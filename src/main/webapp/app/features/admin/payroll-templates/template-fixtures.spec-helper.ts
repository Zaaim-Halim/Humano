import { PayrollTemplate, TemplateEmployee, TemplateInput } from '../models/payroll-template.model';

/** Test data for the payroll template screens' specs. */

export function field(key: string, type: TemplateInput['type'], extra: Partial<TemplateInput> = {}): TemplateInput {
  return { key, type, required: false, values: null, pattern: null, min: null, max: null, defaultValue: null, description: null, ...extra };
}

/** The Netherlands template as the API returns it, trimmed to the inputs the screens use. */
export function nlTemplate(extra: Partial<PayrollTemplate> = {}): PayrollTemplate {
  return {
    id: 'nl-employment',
    name: 'Nederland — loonheffingen',
    version: '2026.1.0',
    status: 'LAW_DERIVED',
    country: 'NL',
    subdivision: null,
    validFrom: '2026-01-01',
    validTo: '2026-12-31',
    currency: 'EUR',
    payFrequencies: ['MONTHLY', 'WEEKLY'],
    taxYearStart: '01-01',
    adoptable: true,
    changelog: [{ version: '2026.1.0', date: '2026-10-21', summary: '2026 rules' }],
    sources: [],
    employerInputs: [
      field('aofPremium', 'ENUM', { required: true, values: ['LOW', 'HIGH'] }),
      field('whkRate', 'DECIMAL', { required: true, min: -1, max: 1 }),
      field('governmentEmployer', 'BOOLEAN', { defaultValue: 'false' }),
    ],
    employeeInputs: [
      field('applyLoonheffingskorting', 'BOOLEAN', { required: true }),
      field('awfPremium', 'ENUM', { required: true, values: ['LOW', 'HIGH'] }),
      field('residentOfNetherlands', 'BOOLEAN', { defaultValue: 'true' }),
    ],
    ledgers: ['INCOME_TAX'],
    limitations: [],
    testCases: 35,
    leavePay: [],
    adoption: null,
    ...extra,
  };
}

export function employee(name: string, missing: string[], attributes: Record<string, string> = {}): TemplateEmployee {
  return {
    employeeId: name.toLowerCase().replace(/ /g, '-'),
    name,
    employeeNumber: null,
    jobTitle: 'Baker',
    department: 'Bakery',
    status: 'ACTIVE',
    attributes,
    missing,
  };
}
