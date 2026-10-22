import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { NEVER, of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';
import { AccountService } from 'app/core/auth/account.service';
import { CurrentEmployeeService } from 'app/features/employee';

import { PayrollRun, PayrollRunService } from '../index';
import PayrollRunComponent from './payroll-run.component';

/** A calculated run that refused a new hire for missing payroll template details. */
const RUN = {
  id: 'run-1',
  status: 'CALCULATED',
  validationErrors: [
    {
      employeeId: 'sem-bakker',
      employeeName: 'Sem Bakker',
      errorCode: 'TEMPLATE_PROFILE_INCOMPLETE',
      message: 'Missing required employee attributes [applyLoonheffingskorting, awfPremium]',
      severity: 'ERROR',
      templateId: 'nl-employment',
    },
  ],
} as unknown as PayrollRun;

describe('PayrollRunComponent', () => {
  function render(canEditTemplateDetails: boolean): HTMLElement {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        // The rendered page uses the app's icon set.
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        { provide: AccountService, useValue: { hasPermission: () => canEditTemplateDetails, hasAnyPermission: () => true } },
        { provide: CurrentEmployeeService, useValue: { resolve: () => undefined, currentEmployeeId: () => null } },
        // The figures stay loading: only the run (and its refused employees) matter here.
        { provide: PayrollRunService, useValue: { find: () => of(RUN), summary: () => NEVER, results: () => NEVER } },
      ],
    });
    const fixture = TestBed.createComponent(PayrollRunComponent);
    fixture.componentRef.setInput('id', 'run-1');
    fixture.detectChanges();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('links an employee refused for missing template details to those details', () => {
    const link = render(true).querySelector<HTMLAnchorElement>('a[href*="/payroll/templates/nl-employment"]');

    expect(link).not.toBeNull();
    expect(link!.getAttribute('href')).toBe('/payroll/templates/nl-employment?employee=sem-bakker');
  });

  it('shows the error without the link to someone who may not edit template details', () => {
    const page = render(false);

    expect(page.textContent).toContain('Missing required employee attributes');
    expect(page.querySelector('a[href*="/payroll/templates"]')).toBeNull();
  });
});
