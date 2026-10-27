import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';

import { PerformanceReview, PerformanceReviewStatus } from '../models/performance-review.model';
import { ReviewCyclePhase } from '../models/review-cycle.model';
import { FeedbackRequestStatus, ReviewFeedback } from '../models/review-feedback.model';
import { ReviewCycleService } from '../services/review-cycle.service';
import { ReviewFeedbackService } from '../services/review-feedback.service';
import PerformanceReviewsComponent from './performance-reviews.component';

/** Component internals under test (protected in the class). */
interface Queue {
  toWrite: { set: (r: PerformanceReview[]) => void };
  counts: () => { todo: number; done: number; all: number };
  nextForReviewer: (r: PerformanceReview) => string;
  asked: { set: (r: ReviewFeedback[]) => void };
  feedbackQueue: () => ReviewFeedback[];
}

function asked(id: string, status: FeedbackRequestStatus, open: boolean, dueDate: string): ReviewFeedback {
  return {
    id,
    reviewId: `r-${id}`,
    cycleId: 'c-1',
    cycleName: '2026 Annual Review',
    employeeId: id,
    employeeName: id,
    employeeJobTitle: null,
    contributorId: 'me',
    contributorName: 'Me',
    contributorJobTitle: null,
    relationship: 'PEER',
    status,
    requestedById: 'maria',
    requestedByName: 'Maria',
    requestedAt: null,
    dueDate,
    message: null,
    strengths: null,
    improvements: null,
    comments: null,
    submittedAt: null,
    declineReason: null,
    visibility: 'MANAGER_ONLY',
    open,
  };
}

function review(id: string, cyclePhase: ReviewCyclePhase, status: PerformanceReviewStatus): PerformanceReview {
  return {
    id,
    employeeId: id,
    employeeName: id,
    employeeJobTitle: null,
    departmentName: null,
    reviewerId: 'maria',
    reviewerName: 'Maria',
    reviewDate: '2026-12-31',
    comments: null,
    rating: null,
    cycleId: 'c-1',
    cycleName: '2026 Annual Review',
    cycleType: 'REGULAR',
    cyclePhase,
    phaseDeadline: null,
    status,
    selfAssessment: null,
    managerAssessment: null,
    feedbackNotes: null,
    sharedAt: null,
    acknowledgement: null,
    signOff: null,
  };
}

describe('PerformanceReviewsComponent', () => {
  let queue: Queue;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ReviewCycleService, useValue: { myReviews: () => of([]), reviewsToWrite: () => of([]) } },
        { provide: ReviewFeedbackService, useValue: { mine: () => of([]) } },
        { provide: AccountService, useValue: { hasPermission: () => false } },
      ],
    });
    queue = TestBed.createComponent(PerformanceReviewsComponent).componentInstance as unknown as Queue;
  });

  it('counts nothing as done while self-assessments are still open', () => {
    queue.toWrite.set([review('a', 'SELF_ASSESSMENT', 'NOT_STARTED'), review('b', 'SELF_ASSESSMENT', 'SELF_SUBMITTED')]);

    expect(queue.counts()).toEqual({ todo: 0, done: 0, all: 2 });
  });

  it('asks for the reviews still to write during manager review', () => {
    queue.toWrite.set([review('a', 'MANAGER_REVIEW', 'SELF_SUBMITTED'), review('b', 'MANAGER_REVIEW', 'MANAGER_SUBMITTED')]);

    expect(queue.counts()).toEqual({ todo: 1, done: 1, all: 2 });
  });

  it('asks for feedback meetings on written reviews only, and never for one that was missed', () => {
    const missed = review('c', 'FEEDBACK_DELIVERY', 'NOT_STARTED');
    queue.toWrite.set([review('a', 'FEEDBACK_DELIVERY', 'MANAGER_SUBMITTED'), review('b', 'FEEDBACK_DELIVERY', 'SHARED'), missed]);

    expect(queue.counts()).toEqual({ todo: 1, done: 1, all: 3 });
    expect(queue.nextForReviewer(missed)).toBe('humano.performance.list.queue.missed');
  });

  it('lists the feedback still to give first, soonest due first', () => {
    queue.asked.set([
      asked('given', 'SUBMITTED', true, '2026-11-01'),
      asked('later', 'REQUESTED', true, '2026-11-20'),
      asked('closed', 'REQUESTED', false, '2026-11-02'),
      asked('sooner', 'REQUESTED', true, '2026-11-10'),
    ]);

    expect(queue.feedbackQueue().map(f => f.id)).toEqual(['sooner', 'later', 'given', 'closed']);
  });
});
