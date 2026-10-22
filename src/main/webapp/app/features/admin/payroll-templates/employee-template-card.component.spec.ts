import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';
import { AccountService } from 'app/core/auth/account.service';

import { EmployeeTemplate, PayrollTemplate } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { EmployeeTemplateCardComponent } from './employee-template-card.component';
import { employee, nlTemplate } from './template-fixtures.spec-helper';
import { ProfileSubject } from './template-profile-drawer.component';

/** Component internals under test (protected in the class). */
interface Card {
  entries: () => EmployeeTemplate[];
  editing: () => { template: PayrollTemplate; subject: ProfileSubject } | null;
  edit: (e: EmployeeTemplate) => void;
}

const COVERED: EmployeeTemplate = {
  templateId: 'nl-employment',
  templateName: 'Nederland — loonheffingen',
  country: 'NL',
  employee: employee('Sem Bakker', ['applyLoonheffingskorting', 'awfPremium']),
};

describe('EmployeeTemplateCardComponent', () => {
  let templatesFor: jest.Mock;

  function open(canManageTemplates: boolean): { card: Card; element: HTMLElement } {
    templatesFor = jest.fn(() => of([COVERED]));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        // The rendered card uses the app's icon set.
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        { provide: AccountService, useValue: { hasPermission: () => canManageTemplates } },
        { provide: PayrollTemplateService, useValue: { templatesFor, get: () => of(nlTemplate()) } },
      ],
    });
    const fixture = TestBed.createComponent(EmployeeTemplateCardComponent);
    fixture.componentRef.setInput('employeeId', 'sem-bakker');
    fixture.detectChanges();
    return { card: fixture.componentInstance as unknown as Card, element: fixture.nativeElement as HTMLElement };
  }

  it('shows which details a covered employee still lacks', () => {
    const { card, element } = open(true);

    expect(templatesFor).toHaveBeenCalledWith('sem-bakker');
    expect(card.entries()).toEqual([COVERED]);
    expect(element.textContent).toContain('humano.payrollTemplates.card.missing');
  });

  it('opens the details panel with the template it needs', () => {
    const { card } = open(true);

    card.edit(COVERED);

    expect(card.editing()?.template.id).toBe('nl-employment');
    expect(card.editing()?.subject).toEqual({ employeeId: 'sem-bakker', name: 'Sem Bakker', attributes: {} });
  });

  it('shows nothing, and asks for nothing, to a viewer who may not manage payroll templates', () => {
    const { card, element } = open(false);

    expect(templatesFor).not.toHaveBeenCalled();
    expect(card.entries()).toEqual([]);
    expect(element.textContent.trim()).toBe('');
  });
});
