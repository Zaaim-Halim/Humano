import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';

import { lucideIcons } from 'app/config/lucide-icons';

import { PerformanceReview } from '../models/performance-review.model';
import { FeedbackRequestStatus, ReviewFeedback, ReviewFeedbackOverview } from '../models/review-feedback.model';
import { ReviewFeedbackService } from '../services/review-feedback.service';
import { ReviewFeedbackPanelComponent } from './review-feedback-panel.component';

const REVIEW = {
  id: 'r-1',
  employeeId: 'alice',
  employeeName: 'Alice Analyst',
  cycleId: 'c-1',
  cyclePhase: 'SELF_ASSESSMENT',
  status: 'NOT_STARTED',
} as PerformanceReview;

function request(
  id: string | null,
  status: FeedbackRequestStatus,
  requestedById: string | null,
  strengths: string | null = null,
): ReviewFeedback {
  return {
    id,
    reviewId: 'r-1',
    cycleId: 'c-1',
    cycleName: '2026 Annual Review',
    employeeId: 'alice',
    employeeName: 'Alice Analyst',
    employeeJobTitle: null,
    contributorId: id ? `${id}-person` : null,
    contributorName: id ? `Person ${id}` : null,
    contributorJobTitle: null,
    relationship: id ? 'PEER' : null,
    status,
    requestedById,
    requestedByName: null,
    requestedAt: null,
    dueDate: null,
    message: null,
    strengths,
    improvements: null,
    comments: null,
    submittedAt: null,
    declineReason: null,
    visibility: 'SHARED_ANONYMOUS',
    open: true,
  };
}

function overview(o: Partial<ReviewFeedbackOverview>): ReviewFeedbackOverview {
  return {
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
    ...o,
  };
}

function render(o: ReviewFeedbackOverview, isEmployee = false): HTMLElement {
  TestBed.configureTestingModule({
    imports: [TranslateModule.forRoot()],
    providers: [
      importProvidersFrom(LucideAngularModule.pick(lucideIcons)),
      { provide: ReviewFeedbackService, useValue: { forReview: () => of(o) } },
    ],
  });
  const fixture = TestBed.createComponent(ReviewFeedbackPanelComponent);
  fixture.componentRef.setInput('review', REVIEW);
  fixture.componentRef.setInput('isEmployee', isEmployee);
  fixture.detectChanges();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function buttons(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('hum-button')).map(b => b.textContent.trim());
}

describe('ReviewFeedbackPanelComponent', () => {
  it('stays hidden when there is nothing to show or do', () => {
    expect(render(overview({})).querySelector('.hum-perf-card')).toBeNull();
  });

  it('lets the reviewer approve or turn down a nomination and cancel an open request', () => {
    const el = render(
      overview({
        canRequest: true,
        canManage: true,
        activeContributors: 2,
        requests: [request('n', 'PENDING_APPROVAL', 'alice'), request('q', 'REQUESTED', 'maria')],
      }),
    );

    expect(buttons(el)).toEqual([
      'humano.performance.feedback.ask',
      'humano.performance.feedback.approve',
      'humano.performance.feedback.turnDown',
      'humano.performance.feedback.cancel',
    ]);
  });

  it('lets the employee withdraw only their own nominations', () => {
    const el = render(
      overview({
        canNominate: true,
        requests: [request('mine', 'PENDING_APPROVAL', 'alice'), request('theirs', 'PENDING_APPROVAL', 'maria')],
      }),
      true,
    );

    expect(buttons(el)).toEqual(['humano.performance.feedback.nominate', 'humano.performance.feedback.withdraw']);
  });

  it('shows anonymous feedback without names', () => {
    const el = render(
      overview({ visibility: 'SHARED_ANONYMOUS', anonymous: true, requests: [request(null, 'SUBMITTED', null, 'Calm under pressure')] }),
      true,
    );

    expect(el.textContent).toContain('Calm under pressure');
    expect(el.querySelector('hum-avatar')).toBeNull();
  });

  it('explains when anonymous feedback is withheld', () => {
    const el = render(overview({ visibility: 'SHARED_ANONYMOUS', anonymous: true, withheld: true }), true);

    expect(el.textContent).toContain('humano.performance.feedback.withheldTitle');
  });
});
