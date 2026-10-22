import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { BadgeComponent, ButtonComponent, ToastService } from 'app/shared/ui';
import { countryName } from 'app/shared/util/countries';

import { EmployeeTemplate, PayrollTemplate } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { templateInputLabel } from './template-inputs';
import { ProfileSubject, TemplateProfileDrawerComponent } from './template-profile-drawer.component';

/**
 * Payroll template card on an employee's profile: for each switched-on statutory template that
 * covers them (same rule as the template's employee list), whether payroll can calculate them or
 * which details are missing, and their details to fill in — so a new hire is completed where their
 * record is, not discovered when a run refuses them. Renders nothing for viewers without
 * `MANAGE_TAX_BRACKETS` or when no template covers the employee.
 */
@Component({
  selector: 'hum-employee-template-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe, LucideAngularModule, BadgeComponent, ButtonComponent, TemplateProfileDrawerComponent],
  template: `
    @for (e of entries(); track e.templateId) {
      <section class="hum-onb-card">
        <div class="hum-onb-card__head">
          <h3 class="hum-onb-card__title">
            <lucide-icon name="landmark" [size]="16" />{{ 'humano.payrollTemplates.card.title' | translate: { country: country(e) } }}
          </h3>
          @if (e.employee.missing.length) {
            <hum-badge tone="warning" [label]="'humano.payrollTemplates.employees.missing' | translate" />
          } @else {
            <hum-badge tone="success" [label]="'humano.payrollTemplates.employees.complete' | translate" />
          }
        </div>
        <p class="hum-onb-card__meta">
          @if (e.employee.missing.length) {
            {{ 'humano.payrollTemplates.card.missing' | translate: { details: missingLabels(e) } }}
          } @else {
            {{ 'humano.payrollTemplates.card.complete' | translate }}
          }
        </p>
        <div class="hum-onb-card__actions">
          <hum-button
            [variant]="e.employee.missing.length ? 'primary' : 'secondary'"
            size="sm"
            icon="pencil"
            [loading]="opening() === e.templateId"
            (click)="edit(e)"
            >{{
              (e.employee.missing.length ? 'humano.payrollTemplates.card.fillIn' : 'humano.payrollTemplates.card.edit') | translate
            }}</hum-button
          >
          <hum-button variant="ghost" size="sm" icon="landmark" [routerLink]="['/payroll/templates', e.templateId]">{{
            'humano.payrollTemplates.byTemplateOpen' | translate
          }}</hum-button>
        </div>
      </section>
    }
    @if (editing(); as ed) {
      <hum-template-profile-drawer [template]="ed.template" [employee]="ed.subject" (saved)="onSaved()" (closed)="editing.set(null)" />
    }
  `,
})
export class EmployeeTemplateCardComponent {
  readonly employeeId = input.required<string>();

  protected readonly canView: boolean;
  protected readonly entries = signal<EmployeeTemplate[]>([]);
  /** The template whose details are being fetched to open the panel. */
  protected readonly opening = signal<string | null>(null);
  protected readonly editing = signal<{ template: PayrollTemplate; subject: ProfileSubject } | null>(null);

  private readonly service = inject(PayrollTemplateService);
  private readonly toast = inject(ToastService);

  constructor() {
    this.canView = inject(AccountService).hasPermission(Permission.MANAGE_TAX_BRACKETS);
    effect(() => {
      const id = this.employeeId();
      if (id && this.canView) untracked(() => this.load(id));
    });
  }

  protected country(e: EmployeeTemplate): string {
    return countryName(e.country);
  }

  protected missingLabels(e: EmployeeTemplate): string {
    return e.employee.missing.map(templateInputLabel).join(', ');
  }

  /** The panel needs the template's inputs, which the card's summary doesn't carry: fetch it, then open. */
  protected edit(e: EmployeeTemplate): void {
    this.opening.set(e.templateId);
    this.service.get(e.templateId).subscribe({
      next: template => {
        this.opening.set(null);
        this.editing.set({
          template,
          subject: { employeeId: e.employee.employeeId, name: e.employee.name, attributes: e.employee.attributes },
        });
      },
      error: (err: unknown) => {
        this.opening.set(null);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected onSaved(): void {
    this.editing.set(null);
    this.load(this.employeeId());
  }

  /** A failed read shows no card: this is a hint on someone else's page, not that page's error. */
  private load(employeeId: string): void {
    this.service.templatesFor(employeeId).subscribe({
      next: list => this.entries.set(list),
      error: () => this.entries.set([]),
    });
  }
}
