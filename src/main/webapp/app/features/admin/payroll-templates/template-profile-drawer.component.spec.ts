import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';

import { PayrollTemplateService } from '../services/payroll-template.service';
import { TemplateInputsGroup } from './template-inputs';
import { nlTemplate } from './template-fixtures.spec-helper';
import { TemplateProfileDrawerComponent } from './template-profile-drawer.component';

/** Component internals under test (protected in the class). */
interface Panel {
  form: () => TemplateInputsGroup | null;
  save: () => void;
}

describe('TemplateProfileDrawerComponent', () => {
  let saveProfile: jest.Mock;
  let saved: jest.Mock;
  let panel: Panel;

  beforeEach(() => {
    saveProfile = jest.fn(() => of({}));
    saved = jest.fn();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        // The rendered panel uses the app's icon set.
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        { provide: PayrollTemplateService, useValue: { saveProfile } },
      ],
    });
    const fixture = TestBed.createComponent(TemplateProfileDrawerComponent);
    fixture.componentRef.setInput('template', nlTemplate());
    fixture.componentRef.setInput('employee', { employeeId: 'daan', name: 'Daan de Boer', attributes: {} });
    fixture.componentInstance.saved.subscribe(saved);
    fixture.detectChanges();
    panel = fixture.componentInstance as unknown as Panel;
  });

  it('does not answer the tax credit for an employee whose admin never chose', () => {
    panel.form()!.controls['awfPremium'].setValue('LOW');

    panel.save();

    expect(saveProfile).not.toHaveBeenCalled();
    expect(panel.form()!.controls['applyLoonheffingskorting'].hasError('required')).toBe(true);
  });

  it("saves the employee's whole profile once it is answered, and says so", () => {
    panel.form()!.controls['awfPremium'].setValue('LOW');
    panel.form()!.controls['applyLoonheffingskorting'].setValue('true');

    panel.save();

    expect(saveProfile).toHaveBeenCalledWith('nl-employment', 'daan', {
      applyLoonheffingskorting: 'true',
      awfPremium: 'LOW',
      residentOfNetherlands: 'true',
    });
    expect(saved).toHaveBeenCalledTimes(1);
  });
});
