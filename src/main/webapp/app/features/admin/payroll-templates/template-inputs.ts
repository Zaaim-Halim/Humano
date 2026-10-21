import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';

import { TemplateInput } from '../models/payroll-template.model';

/** A switch holds a boolean; every other input a string (as typed or chosen). */
export type TemplateInputValue = string | boolean;
export type TemplateInputsGroup = FormGroup<Record<string, FormControl<TemplateInputValue>>>;

const INTEGER = /^-?\d+$/;
const DECIMAL = /^-?\d+(\.\d+)?$/;

/**
 * A form for a template's declared inputs (employer settings or employee attributes), prefilled
 * with the saved values, else the template's defaults. Format checks (number, range, pattern)
 * always apply; "required" is switched on separately with {@link requireTemplateInputs}, because
 * the server only insists on required values once the template is in use.
 */
export function templateInputsGroup(inputs: TemplateInput[], saved: Partial<Record<string, string>>): TemplateInputsGroup {
  const controls: Record<string, FormControl<TemplateInputValue>> = {};
  for (const input of inputs) {
    const value = saved[input.key] ?? input.defaultValue ?? '';
    controls[input.key] = new FormControl<TemplateInputValue>(input.type === 'BOOLEAN' ? value === 'true' : value, {
      nonNullable: true,
      validators: formatValidators(input),
    });
  }
  return new FormGroup(controls);
}

/** Require (or stop requiring) the template's required inputs. A switch always has a value, so it is left alone. */
export function requireTemplateInputs(group: TemplateInputsGroup, inputs: TemplateInput[], required: boolean): void {
  for (const input of inputs.filter(i => i.required && i.type !== 'BOOLEAN')) {
    const control = group.controls[input.key];
    if (required) {
      control.addValidators(Validators.required);
    } else {
      control.removeValidators(Validators.required);
    }
    control.updateValueAndValidity({ emitEvent: false });
  }
}

/** The values to send: strings, as the server stores them; empty inputs are left out. */
export function templateInputValues(inputs: TemplateInput[], group: TemplateInputsGroup): Record<string, string> {
  const values: Record<string, string> = {};
  for (const input of inputs) {
    const value = group.controls[input.key].value;
    const text = typeof value === 'boolean' ? String(value) : value.trim();
    if (text) values[input.key] = text;
  }
  return values;
}

/** `applyLoonheffingskorting` → "Apply loonheffingskorting". Templates ship keys and descriptions, not labels. */
export function templateInputLabel(key: string): string {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function formatValidators(input: TemplateInput): ValidatorFn[] {
  const validators: ValidatorFn[] = [];
  if (input.type === 'INTEGER') validators.push(Validators.pattern(INTEGER));
  if (input.type === 'DECIMAL') validators.push(Validators.pattern(DECIMAL));
  if (input.type === 'STRING' && input.pattern) validators.push(Validators.pattern(input.pattern));
  if (input.min !== null) validators.push(Validators.min(input.min));
  if (input.max !== null) validators.push(Validators.max(input.max));
  return validators;
}
