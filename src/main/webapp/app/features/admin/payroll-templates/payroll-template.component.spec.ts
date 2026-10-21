import { FormControl } from '@angular/forms';
import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';

import { ConfigurePayrollTemplateRequest, PayrollTemplate, TemplateChangelogEntry } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import PayrollTemplateComponent from './payroll-template.component';
import { TemplateInputsGroup } from './template-inputs';
import { nlTemplate } from './template-fixtures.spec-helper';

/** Component internals under test (protected in the class). */
interface Page {
  enabled: FormControl<boolean>;
  settings: () => TemplateInputsGroup | null;
  warnings: () => string[];
  newChanges: () => TemplateChangelogEntry[];
  save: () => void;
}

describe('PayrollTemplateComponent', () => {
  let configure: jest.Mock;

  function open(template: PayrollTemplate, warnings: string[] = []): Page {
    configure = jest.fn((id: string, body: ConfigurePayrollTemplateRequest) =>
      of({
        template: {
          ...template,
          adoption: { enabled: body.enabled, settings: body.settings, acknowledgedVersion: '2026.1.0', updateAvailable: false },
        },
        warnings,
      }),
    );
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        // The rendered page uses the app's icon set.
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        {
          provide: PayrollTemplateService,
          useValue: { get: () => of(template), configure, acknowledge: () => of(template), employees: () => of([]) },
        },
      ],
    });
    const fixture = TestBed.createComponent(PayrollTemplateComponent);
    fixture.componentRef.setInput('id', template.id);
    fixture.detectChanges();
    return fixture.componentInstance as unknown as Page;
  }

  /** How a template chosen at sign-up starts: adopted, switched off, nothing filled in. */
  const chosenAtSignUp = (): PayrollTemplate =>
    nlTemplate({ adoption: { enabled: false, settings: {}, acknowledgedVersion: '2026.1.0', updateAvailable: false } });

  it('saves a template switched off with its settings half filled in, sending strings', () => {
    const page = open(chosenAtSignUp());
    page.settings()!.controls['aofPremium'].setValue('HIGH');

    page.save();

    expect(configure).toHaveBeenCalledWith('nl-employment', {
      enabled: false,
      settings: { aofPremium: 'HIGH', governmentEmployer: 'false' },
    });
  });

  it('does not switch a template on while a required setting is empty', () => {
    const page = open(chosenAtSignUp());
    page.enabled.setValue(true);

    page.save();

    expect(configure).not.toHaveBeenCalled();
    expect(page.settings()!.controls['whkRate'].hasError('required')).toBe(true);
  });

  it('switches a template on once its settings are filled in, and shows what the server warns about', () => {
    const page = open(chosenAtSignUp(), ['1 of 1 NL employee(s) are missing required details for this template']);
    const settings = page.settings()!;
    settings.controls['aofPremium'].setValue('HIGH');
    settings.controls['whkRate'].setValue('0.0152');
    page.enabled.setValue(true);

    page.save();

    expect(configure).toHaveBeenCalledWith('nl-employment', {
      enabled: true,
      settings: { aofPremium: 'HIGH', whkRate: '0.0152', governmentEmployer: 'false' },
    });
    expect(page.warnings()).toEqual(['1 of 1 NL employee(s) are missing required details for this template']);
  });

  it('refuses a setting outside its range even while switched off', () => {
    const page = open(chosenAtSignUp());
    page.settings()!.controls['whkRate'].setValue('1.5');

    page.save();

    expect(configure).not.toHaveBeenCalled();
  });

  it('never lets a draft be switched on', () => {
    expect(open(nlTemplate({ adoptable: false })).enabled.disabled).toBe(true);
  });

  it('lists only the changes newer than the version last reviewed, newest first', () => {
    const page = open(
      nlTemplate({
        version: '2027.1.0',
        changelog: [
          { version: '2027.1.0', date: '2026-12-01', summary: '2027 rules' },
          { version: '2026.2.0', date: '2026-11-01', summary: 'Correction' },
          { version: '2026.1.0', date: '2026-10-21', summary: '2026 rules' },
        ],
        adoption: {
          enabled: true,
          settings: { aofPremium: 'HIGH', whkRate: '0.0152' },
          acknowledgedVersion: '2026.1.0',
          updateAvailable: true,
        },
      }),
    );

    expect(page.newChanges().map(c => c.version)).toEqual(['2027.1.0', '2026.2.0']);
  });
});
