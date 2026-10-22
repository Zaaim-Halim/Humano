import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import { AlertComponent, ButtonComponent, DrawerComponent, ToastService } from 'app/shared/ui';

import { PayrollTemplate } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { TemplateInputsComponent } from './template-inputs.component';
import { TemplateInputsGroup, requireTemplateInputs, templateInputValues, templateInputsGroup } from './template-inputs';

/** The employee whose details are being edited, as both the template's list and the employee's page know them. */
export interface ProfileSubject {
  employeeId: string;
  name: string;
  attributes: Record<string, string>;
}

/**
 * One employee's details for a payroll template, in a side panel: every detail the template reads,
 * explained by its description, and saved as the employee's whole profile (`PUT .../employees/{id}`).
 * Required details are required here — a profile is saved to make the employee payable — and a
 * required yes/no is never answered on the employee's behalf. Open while `employee` is set.
 */
@Component({
  selector: 'hum-template-profile-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, AlertComponent, ButtonComponent, DrawerComponent, TemplateInputsComponent],
  template: `
    <hum-drawer [open]="!!employee()" [title]="employee()?.name ?? ''" width="560px" hasFooter (closed)="closed.emit()">
      @if (form(); as group) {
        <p class="hum-tpl-detail__note">{{ 'humano.payrollTemplates.employees.oneHint' | translate }}</p>
        <hum-template-inputs
          [inputs]="template().employeeInputs"
          [group]="group"
          idPrefix="tpl-employee"
          requiredKey="humano.payrollTemplates.input.requiredEmployee"
        />
      }
      @if (saveError(); as err) {
        <hum-alert tone="danger" [title]="err" />
      }
      <ng-container hum-drawer-footer>
        <hum-button variant="ghost" (click)="closed.emit()">{{ 'humano.action.cancel' | translate }}</hum-button>
        <hum-button variant="primary" icon="check" [loading]="saving()" (click)="save()">{{
          'humano.payrollTemplates.save' | translate
        }}</hum-button>
      </ng-container>
    </hum-drawer>
  `,
})
export class TemplateProfileDrawerComponent {
  readonly template = input.required<PayrollTemplate>();
  readonly employee = input<ProfileSubject | null>(null);
  /** After a successful save (the panel is then closed by the parent). */
  readonly saved = output();
  readonly closed = output();

  protected readonly form = signal<TemplateInputsGroup | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  private readonly service = inject(PayrollTemplateService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  constructor() {
    // A fresh form for each employee opened, prefilled with their saved details.
    effect(() => {
      const employee = this.employee();
      const inputs = this.template().employeeInputs;
      this.saveError.set(null);
      if (!employee) {
        this.form.set(null);
        return;
      }
      const group = templateInputsGroup(inputs, employee.attributes);
      requireTemplateInputs(group, inputs, true);
      this.form.set(group);
    });
  }

  protected save(): void {
    const employee = this.employee();
    const group = this.form();
    if (!employee || !group) return;
    if (group.invalid) {
      group.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    const template = this.template();
    this.service.saveProfile(template.id, employee.employeeId, templateInputValues(template.employeeInputs, group)).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.translate.instant('humano.payrollTemplates.employees.savedOne', { name: employee.name }));
        this.saved.emit();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.saveError.set(normalizeHttpError(err));
      },
    });
  }
}
