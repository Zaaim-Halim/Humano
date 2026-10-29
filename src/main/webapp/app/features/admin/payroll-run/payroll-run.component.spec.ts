import { ApplicationRef, importProvidersFrom } from '@angular/core';
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

/** The same run, where someone else's sick leave was paid in full for want of a leave pay rule. */
const RUN_WITH_WARNING = {
  ...RUN,
  validationErrors: [
    ...RUN.validationErrors,
    {
      employeeId: 'eva-smit',
      employeeName: 'Eva Smit',
      errorCode: 'LEAVE_WITHOUT_RULE',
      message: 'Sick leave paid in full (3 working days): no leave pay rule for sick leave in Netherlands.',
      severity: 'WARNING',
      templateId: null,
    },
  ],
} as unknown as PayrollRun;

describe('PayrollRunComponent', () => {
  function render(canEditTemplateDetails: boolean, run: PayrollRun = RUN): HTMLElement {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        // The rendered page uses the app's icon set.
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        { provide: AccountService, useValue: { hasPermission: () => canEditTemplateDetails, hasAnyPermission: () => true } },
        { provide: CurrentEmployeeService, useValue: { resolve: () => undefined, currentEmployeeId: () => null } },
        // The figures stay loading: only the run (and its refused employees) matter here.
        { provide: PayrollRunService, useValue: { find: () => of(run), summary: () => NEVER, results: () => NEVER } },
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

  it('lists leave paid in full apart, without counting it as someone the run does not pay', () => {
    const page = render(true, RUN_WITH_WARNING);
    const alerts = Array.from(page.querySelectorAll('hum-alert'));

    expect(alerts).toHaveLength(2);
    expect(alerts[0].textContent).toContain('Sem Bakker');
    expect(alerts[0].textContent).not.toContain('Eva Smit');
    // The reason once, the employee as a bullet under it with their working days.
    expect(alerts[1].querySelector('.hum-run-issues__reason')!.textContent).toContain(
      'Sick leave paid in full: no leave pay rule for sick leave in Netherlands.',
    );
    expect(alerts[1].querySelector('.hum-run-issues__people li')!.textContent).toContain('Eva Smit · 3 working days');
    // Untranslated in tests: the button that leads to Payroll → Leave pay.
    expect(alerts[1].textContent).toContain('humano.payrollRun.setLeavePay');
  });

  it('lists the employees under a shared reason, the first nine until all are asked for', () => {
    const many = {
      ...RUN,
      validationErrors: Array.from({ length: 12 }, (_, i) => ({
        employeeId: `e${i}`,
        employeeName: `Employee ${i}`,
        errorCode: 'CALCULATION_ERROR',
        message: 'No earlier payroll this year is recorded for this employee',
        severity: 'ERROR',
        templateId: null,
      })),
    } as unknown as PayrollRun;
    const page = render(true, many);

    expect(page.querySelectorAll('.hum-run-issues__reason')).toHaveLength(1);
    expect(page.querySelectorAll('.hum-run-issues__people li')).toHaveLength(9);
    page.querySelector<HTMLButtonElement>('.hum-run-issues__toggle')!.click();
    TestBed.inject(ApplicationRef).tick();
    expect(page.querySelectorAll('.hum-run-issues__people li')).toHaveLength(12);
  });
});
