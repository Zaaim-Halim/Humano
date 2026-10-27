import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import {
  CurrentEmployeeService,
  EmployeeService,
  PerformanceReview,
  ReviewCycle,
  ReviewCyclePhase,
  ReviewCycleService,
  ReviewFeedbackService,
} from 'app/features/employee';

import ReviewCycleDetailComponent from './review-cycle-detail.component';

/** Component internals under test (protected in the class). */
interface Page {
  cycle: { set: (c: ReviewCycle) => void };
  reviews: { set: (r: PerformanceReview[]) => void };
  counts: () => Record<string, number>;
  canExclude: (r: PerformanceReview) => boolean;
  canAddPeople: () => boolean;
  previousPhase: () => ReviewCyclePhase | null;
  openMoveBack: () => void;
  backDeadlines: () => { field: string; value: string }[];
  setBackDeadline: (field: string, value: string) => void;
  reason: { set: (r: string) => void };
  moveBack: () => void;
}

const CYCLE = {
  cycleId: 'c-1',
  name: '2026 Annual Review',
  phase: 'MANAGER_REVIEW',
  previousPhase: 'SELF_ASSESSMENT',
  deadlines: {
    selfAssessmentDeadline: '2026-11-15',
    managerReviewDeadline: '2026-11-30',
    calibrationDeadline: null,
    feedbackDeadline: '2026-12-20',
  },
} as unknown as ReviewCycle;

function review(id: string, status: PerformanceReview['status'], reviewerId: string | null = 'maria'): PerformanceReview {
  return { id, employeeId: id, employeeName: id, reviewerId, status } as PerformanceReview;
}

function setup(me = 'hana', permissions = [Permission.CONFIGURE_PERFORMANCE_CYCLES]): { page: Page; service: Record<string, jest.Mock> } {
  const service = {
    find: jest.fn(() => of(CYCLE)),
    reviews: jest.fn(() => of({ content: [] })),
    moveBack: jest.fn(() => of({ ...CYCLE, phase: 'SELF_ASSESSMENT' })),
  };
  TestBed.configureTestingModule({
    imports: [TranslateModule.forRoot()],
    providers: [
      provideRouter([]),
      { provide: ReviewCycleService, useValue: service },
      { provide: ReviewFeedbackService, useValue: { counts: () => of([]) } },
      { provide: EmployeeService, useValue: {} },
      { provide: CurrentEmployeeService, useValue: { currentEmployeeId: signal(me) } },
      { provide: AccountService, useValue: { hasPermission: (p: string) => permissions.includes(p) } },
    ],
  });
  const fixture = TestBed.createComponent(ReviewCycleDetailComponent);
  fixture.componentRef.setInput('id', 'c-1');
  return { page: fixture.componentInstance as unknown as Page, service };
}

describe('ReviewCycleDetailComponent', () => {
  it('lists excluded reviews under their own filter, never as needing a reviewer', () => {
    const { page } = setup();
    page.reviews.set([review('alice', 'SELF_SUBMITTED'), review('bob', 'EXCLUDED', null), review('carol', 'NOT_STARTED', null)]);

    expect(page.counts()['EXCLUDED']).toBe(1);
    expect(page.counts()['unassigned']).toBe(1);
  });

  it("lets HR exclude anyone's open review but their own, and add people until manager reviews end", () => {
    const { page } = setup('hana');
    page.cycle.set(CYCLE);

    expect(page.canExclude(review('alice', 'SELF_SUBMITTED'))).toBe(true);
    expect(page.canExclude(review('hana', 'SELF_SUBMITTED'))).toBe(false);
    expect(page.canExclude(review('bob', 'SHARED'))).toBe(false);
    expect(page.canExclude(review('carol', 'EXCLUDED'))).toBe(false);
    expect(page.canAddPeople()).toBe(true);

    page.cycle.set({ ...CYCLE, phase: 'FEEDBACK_DELIVERY' });
    expect(page.canAddPeople()).toBe(false);
    expect(page.canExclude(review('alice', 'MANAGER_SUBMITTED'))).toBe(true);

    page.cycle.set({ ...CYCLE, phase: 'COMPLETED' });
    expect(page.canExclude(review('alice', 'MANAGER_SUBMITTED'))).toBe(false);
  });

  it('changes nothing about membership without CONFIGURE_PERFORMANCE_CYCLES', () => {
    const { page } = setup('hana', [Permission.MANAGE_PERFORMANCE_REVIEWS]);
    page.cycle.set(CYCLE);

    expect(page.canExclude(review('alice', 'SELF_SUBMITTED'))).toBe(false);
    expect(page.canAddPeople()).toBe(false);
    expect(page.previousPhase()).toBeNull();
  });

  it('goes back to the phase before, re-entering its deadline and the later ones', () => {
    const { page, service } = setup();
    page.cycle.set(CYCLE);
    expect(page.previousPhase()).toBe('SELF_ASSESSMENT');

    page.openMoveBack();
    // No calibration in this cycle: it isn't asked for.
    expect(page.backDeadlines().map(d => d.field)).toEqual(['selfAssessmentDeadline', 'managerReviewDeadline', 'feedbackDeadline']);
    page.setBackDeadline('selfAssessmentDeadline', '2026-11-25');
    page.reason.set('  Opened too early  ');
    page.moveBack();

    expect(service.moveBack).toHaveBeenCalledWith('c-1', {
      reason: 'Opened too early',
      selfAssessmentDeadline: '2026-11-25',
      managerReviewDeadline: '2026-11-30',
      feedbackDeadline: '2026-12-20',
    });

    page.cycle.set({ ...CYCLE, phase: 'SELF_ASSESSMENT', previousPhase: null });
    expect(page.previousPhase()).toBeNull();
  });

  it('asks for the deadlines of the phase the server reopens, even when calibration was skipped', () => {
    const { page, service } = setup();
    // Feedback started straight from manager review although the cycle has a calibration deadline.
    page.cycle.set({
      ...CYCLE,
      phase: 'FEEDBACK_DELIVERY',
      previousPhase: 'MANAGER_REVIEW',
      deadlines: { ...CYCLE.deadlines, calibrationDeadline: '2026-12-05' },
    });

    page.openMoveBack();
    expect(page.backDeadlines().map(d => d.field)).toEqual(['managerReviewDeadline', 'calibrationDeadline', 'feedbackDeadline']);
    page.setBackDeadline('managerReviewDeadline', '2026-12-01');
    page.reason.set('Calibration needed after all');
    page.moveBack();

    expect(service.moveBack).toHaveBeenCalledWith('c-1', expect.objectContaining({ managerReviewDeadline: '2026-12-01' }));
  });
});
