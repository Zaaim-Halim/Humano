import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';

import { PerformanceReview } from '../models/performance-review.model';
import { ReviewFeedbackOverview } from '../models/review-feedback.model';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { ReviewCycleService } from '../services/review-cycle.service';
import { ReviewFeedbackService } from '../services/review-feedback.service';
import PerformanceReviewComponent from './performance-review.component';

/** Component internals under test (protected in the class). */
interface Workspace {
  review: { set: (r: PerformanceReview) => void };
  canWriteSelf: () => boolean;
  canWriteReview: () => boolean;
  canRecordMeeting: () => boolean;
  canCalibrateNow: () => boolean;
  showSelfForm: () => boolean;
  showReviewForm: () => boolean;
  selfForm: { setValue: (v: object) => void };
  submitSelf: () => void;
  calibrationRating: { set: (r: number | null) => void };
  calibrationNote: { set: (n: string) => void };
  calibrationNeedsNote: () => boolean;
  canAcknowledge: () => boolean;
  canResolve: () => boolean;
  ackDisputed: { set: (d: boolean) => void };
  ackReasonMissing: () => boolean;
}

const REVIEW: PerformanceReview = {
  id: 'r-1',
  employeeId: 'alice',
  employeeName: 'Alice Analyst',
  employeeJobTitle: 'Analyst',
  departmentName: 'Finance',
  reviewerId: 'maria',
  reviewerName: 'Maria Manager',
  reviewDate: '2026-12-31',
  comments: null,
  rating: null,
  cycleId: 'c-1',
  cycleName: '2026 Annual Review',
  cycleType: 'REGULAR',
  cyclePhase: 'SELF_ASSESSMENT',
  phaseDeadline: '2026-11-15',
  status: 'NOT_STARTED',
  selfAssessment: null,
  managerAssessment: null,
  feedbackNotes: null,
  sharedAt: null,
  acknowledgement: null,
  signOff: null,
};

const noFeedback: ReviewFeedbackOverview = {
  visibility: 'MANAGER_ONLY',
  anonymous: false,
  withheld: false,
  minimumResponses: 3,
  maxContributors: 8,
  activeContributors: 0,
  canRequest: false,
  canNominate: false,
  canManage: false,
  requests: [],
};

function setup(me: string, permissions: string[] = []): { workspace: Workspace; service: Record<string, jest.Mock> } {
  const service = {
    review: jest.fn(() => of(REVIEW)),
    submitSelfAssessment: jest.fn(() => of({ ...REVIEW, status: 'SELF_SUBMITTED' })),
    ratingChanges: jest.fn(() => of([])),
  };
  TestBed.configureTestingModule({
    imports: [TranslateModule.forRoot()],
    providers: [
      provideRouter([]),
      { provide: ReviewCycleService, useValue: service },
      { provide: ReviewFeedbackService, useValue: { forReview: () => of(noFeedback) } },
      { provide: CurrentEmployeeService, useValue: { currentEmployeeId: signal(me), resolved: signal(true), resolve: () => undefined } },
      { provide: AccountService, useValue: { hasPermission: (p: string) => permissions.includes(p) } },
    ],
  });
  const fixture = TestBed.createComponent(PerformanceReviewComponent);
  fixture.componentRef.setInput('id', 'r-1');
  return { workspace: fixture.componentInstance as unknown as Workspace, service };
}

describe('PerformanceReviewComponent', () => {
  it('lets the employee write their self-assessment, and nothing on the reviewer side', () => {
    const { workspace } = setup('alice', [Permission.MANAGE_PERFORMANCE_REVIEWS, Permission.MANAGE_CALIBRATION]);

    workspace.review.set({ ...REVIEW, cyclePhase: 'MANAGER_REVIEW' });
    expect(workspace.canWriteSelf()).toBe(true);
    expect(workspace.showSelfForm()).toBe(true);
    expect(workspace.canWriteReview()).toBe(false);

    workspace.review.set({ ...REVIEW, cyclePhase: 'CALIBRATION', status: 'MANAGER_SUBMITTED', rating: 4 });
    expect(workspace.canWriteSelf()).toBe(false);
    expect(workspace.canCalibrateNow()).toBe(false);
  });

  it('lets the reviewer write the review during manager review, and HR managing reviews too', () => {
    const reviewer = setup('maria').workspace;
    reviewer.review.set({ ...REVIEW, cyclePhase: 'MANAGER_REVIEW', status: 'SELF_SUBMITTED' });
    expect(reviewer.canWriteReview()).toBe(true);
    expect(reviewer.canWriteSelf()).toBe(false);

    TestBed.resetTestingModule();
    const colleague = setup('bob').workspace;
    colleague.review.set({ ...REVIEW, cyclePhase: 'MANAGER_REVIEW', status: 'SELF_SUBMITTED' });
    expect(colleague.canWriteReview()).toBe(false);

    TestBed.resetTestingModule();
    const hr = setup('hana', [Permission.MANAGE_PERFORMANCE_REVIEWS]).workspace;
    hr.review.set({ ...REVIEW, cyclePhase: 'MANAGER_REVIEW', status: 'SELF_SUBMITTED' });
    expect(hr.canWriteReview()).toBe(true);
    expect(hr.showReviewForm()).toBe(false); // writing on the reviewer's behalf is a deliberate step
    expect(reviewer.showReviewForm()).toBe(true);

    hr.review.set({ ...REVIEW, cyclePhase: 'FEEDBACK_DELIVERY', status: 'MANAGER_SUBMITTED' });
    expect(hr.canWriteReview()).toBe(false);
    expect(hr.canRecordMeeting()).toBe(true);
  });

  it('lets nobody work on a review taken out of its cycle', () => {
    const excluded: PerformanceReview = { ...REVIEW, cyclePhase: 'MANAGER_REVIEW', status: 'EXCLUDED' };
    const reviewer = setup('maria').workspace;
    reviewer.review.set(excluded);
    expect(reviewer.canWriteReview()).toBe(false);

    TestBed.resetTestingModule();
    const hr = setup('hana', [Permission.MANAGE_PERFORMANCE_REVIEWS]).workspace;
    hr.review.set(excluded);
    expect(hr.canWriteReview()).toBe(false);

    TestBed.resetTestingModule();
    const employee = setup('alice').workspace;
    employee.review.set(excluded);
    expect(employee.canWriteSelf()).toBe(false);
  });

  it('submits the self-assessment trimmed, leaving out blank optional answers', () => {
    const { workspace, service } = setup('alice');
    workspace.review.set(REVIEW);
    workspace.selfForm.setValue({
      achievements: '  Shipped billing  ',
      challenges: ' ',
      developmentGoals: 'Lead a project',
      additionalComments: '',
    });

    workspace.submitSelf();

    expect(service.submitSelfAssessment).toHaveBeenCalledWith('c-1', {
      achievements: 'Shipped billing',
      developmentGoals: 'Lead a project',
    });
  });

  it('asks for a reason only when calibration changes the rating', () => {
    const { workspace } = setup('hana', [Permission.MANAGE_CALIBRATION]);
    workspace.review.set({
      ...REVIEW,
      cyclePhase: 'CALIBRATION',
      status: 'MANAGER_SUBMITTED',
      rating: 4,
      managerAssessment: {
        rating: 4,
        strengths: 'Ownership',
        areasForImprovement: null,
        developmentRecommendations: null,
        recommendForPromotion: false,
        promotionJustification: null,
        probationRecommendation: null,
        calibrationNote: null,
        potential: null,
        submittedAt: '2026-11-20T10:00:00Z',
      },
    });
    expect(workspace.canCalibrateNow()).toBe(true);

    workspace.calibrationRating.set(4);
    expect(workspace.calibrationNeedsNote()).toBe(false);

    workspace.calibrationRating.set(3);
    expect(workspace.calibrationNeedsNote()).toBe(true);

    workspace.calibrationNote.set('Aligned with peers at the same level');
    expect(workspace.calibrationNeedsNote()).toBe(false);
  });

  it('asks the employee to acknowledge their shared review, with a reason to dispute it', () => {
    const shared: PerformanceReview = { ...REVIEW, cyclePhase: 'FEEDBACK_DELIVERY', status: 'SHARED', rating: 3 };
    const { workspace } = setup('alice');
    workspace.review.set(shared);
    expect(workspace.canAcknowledge()).toBe(true);
    expect(workspace.ackReasonMissing()).toBe(false);
    workspace.ackDisputed.set(true);
    expect(workspace.ackReasonMissing()).toBe(true);

    TestBed.resetTestingModule();
    const reviewer = setup('maria').workspace;
    reviewer.review.set(shared);
    expect(reviewer.canAcknowledge()).toBe(false);
    expect(reviewer.canRecordMeeting()).toBe(true);

    reviewer.review.set({ ...shared, acknowledgement: acknowledgement(true) });
    expect(reviewer.canRecordMeeting()).toBe(false); // the notes are locked once acknowledged
  });

  it('lets HR outside the dispute resolve it, never the reviewer', () => {
    const disputed: PerformanceReview = { ...REVIEW, cyclePhase: 'COMPLETED', status: 'SHARED', acknowledgement: acknowledgement(true) };
    const hr = setup('hana', [Permission.MANAGE_PERFORMANCE_REVIEWS]).workspace;
    hr.review.set(disputed);
    expect(hr.canResolve()).toBe(true);

    hr.review.set({ ...disputed, acknowledgement: { ...acknowledgement(true), resolvedAt: '2026-12-01T10:00:00Z' } });
    expect(hr.canResolve()).toBe(false);

    TestBed.resetTestingModule();
    const reviewerWithHrRights = setup('maria', [Permission.MANAGE_PERFORMANCE_REVIEWS]).workspace;
    reviewerWithHrRights.review.set(disputed);
    expect(reviewerWithHrRights.canResolve()).toBe(false);
  });
});

function acknowledgement(disputed: boolean): NonNullable<PerformanceReview['acknowledgement']> {
  return {
    acknowledgedAt: '2026-11-30T10:00:00Z',
    comment: disputed ? 'The Q3 launch is missing' : null,
    disputed,
    resolution: null,
    resolvedAt: null,
    resolvedByName: null,
  };
}
