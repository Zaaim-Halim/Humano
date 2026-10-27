import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';
import { EmployeeService, PerformanceFrameworkService, ReviewCycleProposal, ReviewCycleService } from 'app/features/employee';

import { DepartmentService } from '../services/department.service';
import { ReviewCycleFormComponent } from './review-cycle-form.component';

/** Component internals under test (protected in the class). */
interface Form {
  form: { patchValue: (v: object) => void };
  dateError: () => { key: string } | null;
  submit: () => void;
}

const PROPOSAL: ReviewCycleProposal = {
  name: '2026 Annual Review',
  reviewPeriodStart: '2026-01-01',
  reviewPeriodEnd: '2026-12-31',
  startDate: '2026-12-17',
  endDate: '2027-02-04',
  selfAssessmentDeadline: '2026-12-31',
  managerReviewDeadline: '2027-01-14',
  calibrationDeadline: '2027-01-21',
  feedbackDeadline: '2027-02-04',
};

describe('ReviewCycleFormComponent', () => {
  let form: Form;
  let create: jest.Mock;

  beforeEach(() => {
    create = jest.fn(() => of({ cycleId: 'c-1', name: PROPOSAL.name }));
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
        { provide: ReviewCycleService, useValue: { create } },
        { provide: DepartmentService, useValue: { query: () => of({ content: [] }) } },
        { provide: EmployeeService, useValue: { searchOptions: () => of([]) } },
        { provide: PerformanceFrameworkService, useValue: { templates: () => of([]) } },
      ],
    });
    const fixture = TestBed.createComponent(ReviewCycleFormComponent);
    fixture.componentRef.setInput('proposal', PROPOSAL);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    form = fixture.componentInstance as unknown as Form;
  });

  it("opens on the calendar's proposal, with the dates in order", () => {
    expect(form.dateError()).toBeNull();
  });

  it('flags deadlines out of phase order or after the cycle closes', () => {
    form.form.patchValue({ managerReviewDeadline: '2026-12-20' });
    expect(form.dateError()?.key).toBe('humano.performance.form.error.before');

    form.form.patchValue({ managerReviewDeadline: '2027-01-14', feedbackDeadline: '2027-03-01' });
    expect(form.dateError()?.key).toBe('humano.performance.form.error.after');
  });

  it('ignores the calibration deadline when the cycle has no calibration', () => {
    form.form.patchValue({ calibrate: false, calibrationDeadline: '2026-01-01' });
    expect(form.dateError()).toBeNull();

    form.submit();

    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ calibrationDeadline: null, departmentIds: [], includeAllDepartments: true, name: '2026 Annual Review' }),
    );
  });

  it('collects feedback from up to 8 colleagues, for the reviewer only, by default', () => {
    form.submit();

    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ feedbackVisibility: 'MANAGER_ONLY', allowFeedbackNominations: true, maxContributors: 8 }),
    );
  });

  it('sends no contributors when feedback from colleagues is off', () => {
    form.form.patchValue({ collectFeedback: false, maxContributors: 50 });

    form.submit();

    expect(create).toHaveBeenCalledWith(expect.objectContaining({ maxContributors: 0 }));
  });
});
