import { TemplateInput } from '../models/payroll-template.model';
import { isTemplateSwitch, requireTemplateInputs, templateInputLabel, templateInputValues, templateInputsGroup } from './template-inputs';

function field(key: string, type: TemplateInput['type'], extra: Partial<TemplateInput> = {}): TemplateInput {
  return { key, type, required: false, values: null, pattern: null, min: null, max: null, defaultValue: null, description: null, ...extra };
}

// The Netherlands template's employer settings.
const NL: TemplateInput[] = [
  field('aofPremium', 'ENUM', { required: true, values: ['LOW', 'HIGH'] }),
  field('whkRate', 'DECIMAL', { required: true, min: -1, max: 1 }),
  field('governmentEmployer', 'BOOLEAN', { defaultValue: 'false' }),
];

describe('payroll template inputs', () => {
  it('prefills saved values, else the template defaults', () => {
    const group = templateInputsGroup(NL, { whkRate: '0.0152' });
    expect(group.controls['aofPremium'].value).toBe('');
    expect(group.controls['whkRate'].value).toBe('0.0152');
    expect(group.controls['governmentEmployer'].value).toBe(false);
  });

  it('requires the required inputs only while asked to (the template is switched on)', () => {
    const group = templateInputsGroup(NL, {});
    expect(group.valid).toBe(true);
    requireTemplateInputs(group, NL, true);
    group.updateValueAndValidity();
    expect(group.controls['aofPremium'].hasError('required')).toBe(true);
    expect(group.controls['whkRate'].hasError('required')).toBe(true);
    requireTemplateInputs(group, NL, false);
    group.updateValueAndValidity();
    expect(group.valid).toBe(true);
  });

  it('checks numbers and their range', () => {
    const group = templateInputsGroup(NL, { whkRate: '1.5' });
    expect(group.controls['whkRate'].hasError('max')).toBe(true);
    group.controls['whkRate'].setValue('1,5');
    expect(group.controls['whkRate'].hasError('pattern')).toBe(true);
  });

  it('sends strings, leaving empty inputs out', () => {
    const group = templateInputsGroup(NL, { aofPremium: 'HIGH' });
    group.controls['whkRate'].setValue('  ');
    group.controls['governmentEmployer'].setValue(true);
    expect(templateInputValues(NL, group)).toEqual({ aofPremium: 'HIGH', governmentEmployer: 'true' });
  });

  it('asks for a required yes/no with no default instead of answering "no" for the user', () => {
    // The Netherlands template's tax credit: someone has to decide, so it can't start as an "off" switch.
    const credit = field('applyLoonheffingskorting', 'BOOLEAN', { required: true });
    const resident = field('residentOfNetherlands', 'BOOLEAN', { defaultValue: 'true' });
    expect(isTemplateSwitch(credit)).toBe(false);
    expect(isTemplateSwitch(resident)).toBe(true);

    const group = templateInputsGroup([credit, resident], {});
    expect(group.controls['applyLoonheffingskorting'].value).toBe('');
    expect(templateInputValues([credit, resident], group)).toEqual({ residentOfNetherlands: 'true' });
    requireTemplateInputs(group, [credit, resident], true);
    expect(group.controls['applyLoonheffingskorting'].hasError('required')).toBe(true);

    group.controls['applyLoonheffingskorting'].setValue('false');
    expect(group.valid).toBe(true);
    expect(templateInputValues([credit], group)).toEqual({ applyLoonheffingskorting: 'false' });
    expect(templateInputsGroup([credit], { applyLoonheffingskorting: 'true' }).controls['applyLoonheffingskorting'].value).toBe('true');
  });

  it('labels a key as words', () => {
    expect(templateInputLabel('applyLoonheffingskorting')).toBe('Apply loonheffingskorting');
    expect(templateInputLabel('whkRate')).toBe('Whk rate');
    expect(templateInputLabel('state_code')).toBe('State code');
  });
});
