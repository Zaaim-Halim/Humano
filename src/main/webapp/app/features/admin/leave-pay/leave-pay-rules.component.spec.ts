import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { LeaveType } from 'app/features/employee/models/enums/leave-type.enum';
import { LeavePayRule } from '../models/leave-pay-rule.model';
import { CountryService } from '../services/country.service';
import { LeavePayRuleService } from '../services/leave-pay-rule.service';
import LeavePayRulesComponent from './leave-pay-rules.component';

/** Component internals under test (protected in the class). */
interface Screen {
  draft: () => {
    id: string | null;
    countryId: string;
    bands: { key: number; fromDay: number | null; toDay: number | null; paid: number | null }[];
    otherwisePaid: number | null;
  } | null;
  valid: () => boolean;
  duplicate: () => boolean;
  bandErrors: () => { fromDay: boolean; toDay: boolean; paid: boolean; overlap: boolean }[];
  openNew: () => void;
  openEdit: (rule: LeavePayRule) => void;
  patch: (patch: object) => void;
  patchBand: (key: number, patch: object) => void;
  addBand: () => void;
  save: () => void;
  summary: (rule: LeavePayRule) => string[];
}

const SICK_DE: LeavePayRule = {
  id: 'r1',
  countryId: 'de',
  countryCode: 'DE',
  countryName: 'Germany',
  leaveType: LeaveType.SICK,
  deductionPercentage: 100,
  payBands: [{ fromDay: 1, toDay: 42, deductionPercentage: 0 }],
  affectsTaxableSalary: true,
  description: 'SICK - by pay band to day 42, then Unpaid',
};

describe('LeavePayRulesComponent', () => {
  let screen: Screen;
  let create: jest.Mock;
  let update: jest.Mock;

  beforeEach(() => {
    create = jest.fn((body: object) => of({ ...SICK_DE, ...body, id: 'r2' }));
    update = jest.fn((id: string, body: object) => of({ ...SICK_DE, ...body, id }));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: LeavePayRuleService, useValue: { list: () => of([SICK_DE]), create, update, delete: jest.fn() } },
        {
          provide: CountryService,
          useValue: {
            list: () =>
              of([
                { id: 'de', code: 'DE', name: 'Germany' },
                { id: 'fr', code: 'FR', name: 'France' },
              ]),
          },
        },
      ],
    });
    screen = TestBed.createComponent(LeavePayRulesComponent).componentInstance as unknown as Screen;
  });

  it('shows the paid share of each band when editing and sends the unpaid share back', () => {
    screen.openEdit(SICK_DE);

    expect(screen.draft()!.bands.map(b => b.paid)).toEqual([100]);
    expect(screen.draft()!.otherwisePaid).toBe(0);
    expect(screen.valid()).toBe(true);

    screen.patch({ otherwisePaid: 33.33 });
    screen.save();

    expect(update).toHaveBeenCalledWith('r1', {
      deductionPercentage: 66.67,
      payBands: [{ fromDay: 1, toDay: 42, deductionPercentage: 0 }],
      affectsTaxableSalary: true,
    });
  });

  it('starts a new rule for the only country in use, unpaid and before tax', () => {
    screen.openNew();
    screen.patch({ leaveType: LeaveType.UNPAID });

    expect(screen.draft()!.countryId).toBe('de');
    expect(screen.valid()).toBe(true);
    screen.save();
    expect(create).toHaveBeenCalledWith({
      countryId: 'de',
      leaveType: LeaveType.UNPAID,
      deductionPercentage: 100,
      payBands: [],
      affectsTaxableSalary: true,
    });
  });

  it('refuses a second rule for the same country and leave type', () => {
    screen.openNew();
    screen.patch({ leaveType: LeaveType.SICK });

    expect(screen.duplicate()).toBe(true);
    expect(screen.valid()).toBe(false);
  });

  it('flags overlapping and malformed bands', () => {
    screen.openEdit(SICK_DE);
    screen.addBand();
    const [first, second] = screen.draft()!.bands;

    // The new band starts the day after the last one ended.
    expect(second.fromDay).toBe(43);
    screen.patchBand(second.key, { fromDay: 40 });
    expect(screen.bandErrors()[1].overlap).toBe(true);

    screen.patchBand(second.key, { fromDay: 43, toDay: 10 });
    expect(screen.bandErrors()[1]).toEqual({ fromDay: false, toDay: true, paid: false, overlap: false });

    screen.patchBand(first.key, { paid: 120 });
    expect(screen.bandErrors()[0].paid).toBe(true);
    expect(screen.valid()).toBe(false);
  });

  it('summarizes the bands and the rate after them', () => {
    expect(screen.summary(SICK_DE)).toEqual(['humano.leavePay.summary.band', 'humano.leavePay.summary.after']);
    expect(screen.summary({ ...SICK_DE, payBands: [] })).toEqual(['humano.leavePay.summary.everyDay']);
    expect(screen.summary({ ...SICK_DE, payBands: [{ fromDay: 1, toDay: null, deductionPercentage: 30 }] })).toEqual([
      'humano.leavePay.summary.bandOpen',
    ]);
  });
});
