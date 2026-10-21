import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';

import { FormFieldComponent, InputComponent, SelectComponent, SelectOption, SwitchComponent } from 'app/shared/ui';

import { TemplateInput } from '../models/payroll-template.model';
import { TemplateInputsGroup, templateInputLabel } from './template-inputs';

/**
 * The fields for a payroll template's declared inputs — a choice for ENUM, a switch for BOOLEAN, a
 * number for INTEGER/DECIMAL, text otherwise — each explained by the template's own description.
 * Bound to a group from {@link templateInputsGroup}.
 */
@Component({
  selector: 'hum-template-inputs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, FormFieldComponent, InputComponent, SelectComponent, SwitchComponent],
  template: `
    <div class="hum-set-grid hum-tpl-inputs" [formGroup]="group()">
      @for (field of inputs(); track field.key) {
        @let id = idPrefix() + '-' + field.key;
        @if (field.type === 'BOOLEAN') {
          <div class="hum-tpl-inputs__switch">
            <hum-switch [formControlName]="field.key" [label]="label(field.key)" />
            @if (field.description; as d) {
              <p class="hum-tpl-inputs__hint">{{ d }}</p>
            }
          </div>
        } @else {
          <hum-form-field
            [label]="label(field.key)"
            [controlId]="id"
            [required]="field.required"
            [hint]="invalid(field.key) ? undefined : (field.description ?? undefined)"
            [error]="invalid(field.key) ? (errorKey(field) | translate: { min: field.min, max: field.max }) : undefined"
          >
            @if (field.type === 'ENUM') {
              <hum-select [formControlName]="field.key" [options]="options(field)" [invalid]="invalid(field.key)" />
            } @else {
              <hum-input
                [inputId]="id"
                [formControlName]="field.key"
                [type]="field.type === 'STRING' ? 'text' : 'number'"
                [mono]="field.type !== 'STRING'"
                [invalid]="invalid(field.key)"
              />
            }
          </hum-form-field>
        }
      }
    </div>
  `,
})
export class TemplateInputsComponent {
  readonly inputs = input.required<TemplateInput[]>();
  readonly group = input.required<TemplateInputsGroup>();
  /** Prefix for the fields' element ids, unique on the page. */
  readonly idPrefix = input('tpl');

  protected label(key: string): string {
    return templateInputLabel(key);
  }

  protected options(field: TemplateInput): SelectOption[] {
    return [{ value: '', label: '—' }, ...(field.values ?? []).map(v => ({ value: v, label: v }))];
  }

  protected invalid(key: string): boolean {
    const c = this.group().controls[key];
    return c.invalid && (c.dirty || c.touched);
  }

  protected errorKey(field: TemplateInput): string {
    const errors = this.group().controls[field.key].errors ?? {};
    if (errors['required']) return 'humano.payrollTemplates.input.required';
    if (errors['min'] || errors['max']) {
      return field.min !== null && field.max !== null
        ? 'humano.payrollTemplates.input.range'
        : errors['min']
          ? 'humano.payrollTemplates.input.min'
          : 'humano.payrollTemplates.input.max';
    }
    if (errors['pattern'] && field.type !== 'STRING') return 'humano.payrollTemplates.input.number';
    return 'humano.payrollTemplates.input.format';
  }
}
