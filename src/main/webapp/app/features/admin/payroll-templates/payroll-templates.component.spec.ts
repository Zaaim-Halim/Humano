import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { PayrollTemplate } from '../models/payroll-template.model';
import { PayrollTemplateService } from '../services/payroll-template.service';
import PayrollTemplatesComponent from './payroll-templates.component';
import { nlTemplate } from './template-fixtures.spec-helper';
import { templateState } from './template-state';

/** Component internals under test (protected in the class). */
interface List {
  groups: () => { key: string; templates: PayrollTemplate[] }[];
}

const ADOPTION = { settings: {}, acknowledgedVersion: '2026.1.0', updateAvailable: false };

describe('PayrollTemplatesComponent', () => {
  function listOf(templates: PayrollTemplate[]): List {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: PayrollTemplateService, useValue: { list: () => of(templates) } }],
    });
    return TestBed.createComponent(PayrollTemplatesComponent).componentInstance as unknown as List;
  }

  it('puts the templates the workspace uses first — in use, then not switched on — and the rest by country', () => {
    const list = listOf([
      nlTemplate({ id: 'us', country: 'US' }),
      nlTemplate({ id: 'bg', country: 'BG', adoption: { ...ADOPTION, enabled: false } }),
      nlTemplate({ id: 'al', country: 'AL' }),
      nlTemplate({ id: 'nl', country: 'NL', adoption: { ...ADOPTION, enabled: true } }),
    ]);

    expect(list.groups().map(g => [g.key, g.templates.map(t => t.id)])).toEqual([
      ['inUse', ['nl', 'bg']],
      ['available', ['al', 'us']],
    ]);
  });

  it('leaves out a group with nothing in it', () => {
    expect(
      listOf([nlTemplate()])
        .groups()
        .map(g => g.key),
    ).toEqual(['available']);
  });
});

describe('templateState', () => {
  it('names where a template stands for the workspace', () => {
    expect(templateState(nlTemplate({ adoption: { ...ADOPTION, enabled: true } })).key).toBe('humano.payrollTemplates.state.on');
    expect(templateState(nlTemplate({ adoption: { ...ADOPTION, enabled: false } })).key).toBe('humano.payrollTemplates.state.off');
    expect(templateState(nlTemplate({ adoptable: false })).key).toBe('humano.payrollTemplates.state.draft');
    expect(templateState(nlTemplate()).key).toBe('humano.payrollTemplates.state.available');
  });
});
