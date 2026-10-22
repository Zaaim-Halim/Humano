import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';

import { TemplateEmployee, TemplateInput } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import { TemplateEmployeesComponent } from './template-employees.component';
import { TemplateInputsGroup } from './template-inputs';
import { employee, nlTemplate } from './template-fixtures.spec-helper';

/** Component internals under test (protected in the class). */
interface Section {
  onlyMissing: { set: (v: boolean) => void };
  search: { set: (v: string) => void };
  selected: () => Set<string>;
  visible: () => TemplateEmployee[];
  missingCount: () => number;
  form: () => TemplateInputsGroup | null;
  saveError: () => string | null;
  editingOne: () => TemplateEmployee | null;
  selectMissing: () => void;
  edit: (row: TemplateEmployee) => void;
  onOneSaved: () => void;
  editSelected: () => void;
  choose: (field: TemplateInput, on: boolean) => void;
  saveMany: () => void;
}

const ROWS = [
  employee('Daan de Boer', ['applyLoonheffingskorting', 'awfPremium']),
  employee('Lotte Jansen', [], { applyLoonheffingskorting: 'true', awfPremium: 'LOW' }),
  employee('Femke Visser', ['applyLoonheffingskorting'], { awfPremium: 'LOW' }),
];

describe('TemplateEmployeesComponent', () => {
  let employees: jest.Mock;
  let updateProfiles: jest.Mock;
  let section: Section;
  const template = nlTemplate({
    adoption: {
      enabled: true,
      settings: { aofPremium: 'HIGH', whkRate: '0.0152' },
      acknowledgedVersion: '2026.1.0',
      updateAvailable: false,
    },
  });
  const input = (key: string): TemplateInput => template.employeeInputs.find(i => i.key === key)!;

  function open(focusEmployeeId: string | null = null): Section {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        // The rendered page uses the app's icon set.
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        { provide: PayrollTemplateService, useValue: { employees, saveProfile: () => of({}), updateProfiles } },
      ],
    });
    const fixture = TestBed.createComponent(TemplateEmployeesComponent);
    fixture.componentRef.setInput('template', template);
    fixture.componentRef.setInput('focusEmployeeId', focusEmployeeId);
    fixture.detectChanges();
    return fixture.componentInstance as unknown as Section;
  }

  beforeEach(() => {
    employees = jest.fn(() => of(ROWS));
    updateProfiles = jest.fn(() => of([]));
    section = open();
  });

  it('counts who still lacks details and can show only them', () => {
    expect(section.missingCount()).toBe(2);
    section.onlyMissing.set(true);
    expect(section.visible().map(r => r.name)).toEqual(['Daan de Boer', 'Femke Visser']);
  });

  it('finds employees by name, job or department', () => {
    section.search.set('femke');
    expect(section.visible().map(r => r.name)).toEqual(['Femke Visser']);
    section.search.set('bakery');
    expect(section.visible()).toHaveLength(3);
  });

  it('selects everyone missing details in one go', () => {
    section.selectMissing();
    expect([...section.selected()]).toEqual(['daan-de-boer', 'femke-visser']);
  });

  it('opens an employee in the one-employee panel', () => {
    section.edit(ROWS[1]);
    expect(section.editingOne()?.name).toBe('Lotte Jansen');
  });

  it('opens the employee a refused payroll run linked to, once: not again when a save reloads the list', () => {
    TestBed.resetTestingModule();
    const linked = open('femke-visser');
    expect(linked.editingOne()?.name).toBe('Femke Visser');

    linked.onOneSaved();

    expect(employees).toHaveBeenCalledTimes(3); // the first open, the linked open, and the reload after saving
    expect(linked.editingOne()).toBeNull();
  });

  it('opens nothing for a linked employee the template does not cover', () => {
    TestBed.resetTestingModule();
    expect(open('someone-else').editingOne()).toBeNull();
  });

  it('sets only the chosen details on every selected employee', () => {
    section.selectMissing();
    section.editSelected();
    section.choose(input('awfPremium'), true);
    section.form()!.controls['awfPremium'].setValue('HIGH');

    section.saveMany();

    expect(updateProfiles).toHaveBeenCalledWith('nl-employment', {
      employeeIds: ['daan-de-boer', 'femke-visser'],
      attributes: { awfPremium: 'HIGH' },
    });
  });

  it('asks for a detail to set before a bulk save, and for its value once chosen', () => {
    section.selectMissing();
    section.editSelected();

    section.saveMany();
    expect(section.saveError()).toBe('humano.payrollTemplates.employees.chooseOne');

    section.choose(input('applyLoonheffingskorting'), true);
    section.saveMany();
    expect(updateProfiles).not.toHaveBeenCalled();
    expect(section.form()!.controls['applyLoonheffingskorting'].hasError('required')).toBe(true);
  });
});
