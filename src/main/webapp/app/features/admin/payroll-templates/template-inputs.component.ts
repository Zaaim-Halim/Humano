import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { FormFieldComponent, InputComponent, SelectComponent, SelectOption, SwitchComponent } from 'app/shared/ui';

import { TemplateInput } from '../models/payroll-template.model';
import { TemplateInputsGroup, isTemplateSwitch, templateInputLabel } from './template-inputs';

/**
 * The fields for a payroll template's declared inputs — a choice for ENUM, a switch for BOOLEAN (a
 * Yes / No choice when it is required with no default, see {@link isTemplateSwitch}), a
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
        @if (isSwitch(field)) {
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
            @if (field.type === 'ENUM' || field.type === 'BOOLEAN') {
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
  /** The message for an empty required field — why it's required depends on the form. */
  readonly requiredKey = input('humano.payrollTemplates.input.required');

  protected readonly isSwitch = isTemplateSwitch;

  private readonly translate = inject(TranslateService);

  protected label(key: string): string {
    return templateInputLabel(key);
  }

  /** An ENUM's values, or Yes / No for a yes/no that must be answered; empty first ("not chosen"). */
  protected options(field: TemplateInput): SelectOption[] {
    const values =
      field.type === 'BOOLEAN'
        ? [
            { value: 'true', label: String(this.translate.instant('humano.payrollTemplates.input.yes')) },
            { value: 'false', label: String(this.translate.instant('humano.payrollTemplates.input.no')) },
          ]
        : (field.values ?? []).map(v => ({ value: v, label: v }));
    return [{ value: '', label: '—' }, ...values];
  }

  protected invalid(key: string): boolean {
    const c = this.group().controls[key];
    return c.invalid && (c.dirty || c.touched);
  }

  protected errorKey(field: TemplateInput): string {
    const errors = this.group().controls[field.key].errors ?? {};
    if (errors['required']) return this.requiredKey();
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
