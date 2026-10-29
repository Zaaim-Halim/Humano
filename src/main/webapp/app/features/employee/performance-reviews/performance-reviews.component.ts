import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SkeletonComponent,
} from 'app/shared/ui';

import { ImprovementPlan } from '../models/improvement-plan.model';
import { PerformanceReview } from '../models/performance-review.model';
import { ReviewFeedback } from '../models/review-feedback.model';
import { ImprovementPlanService } from '../services/improvement-plan.service';
import { ReviewCycleService } from '../services/review-cycle.service';
import { ReviewFeedbackService } from '../services/review-feedback.service';

type QueueFilter = 'todo' | 'done' | 'all';

/**
 * Performance reviews — `/reviews`, for everyone, in running cycles only (past reviews are on
 * the employee's profile):
 *
 * - Your review: the signed-in employee's own review per running cycle and what happens next —
 *   write the self-assessment (with its deadline), wait for the reviewer, wait for the feedback
 *   meeting, acknowledge the shared review, or read it.
 * - To review: the reviews they write as reviewer, filtered to what still needs them (the
 *   manager review during that phase, the feedback meeting during feedback delivery), what's
 *   done, or all, each with whether the employee's self-assessment is in.
 * - Feedback requested from you: colleagues' reviews the signed-in employee was asked to give
 *   feedback on, open ones first.
 */
@Component({
  selector: 'hum-performance-reviews',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    SkeletonComponent,
  ],
  templateUrl: './performance-reviews.component.html',
})
export default class PerformanceReviewsComponent {
  private readonly reviewCycleService = inject(ReviewCycleService);
  private readonly feedbackService = inject(ReviewFeedbackService);
  private readonly planService = inject(ImprovementPlanService);
  private readonly translate = inject(TranslateService);

  protected readonly canSeeCycles = inject(AccountService).hasPermission(Permission.VIEW_PERFORMANCE_REVIEWS);

  protected readonly mine = signal<PerformanceReview[] | null>(null);
  protected readonly toWrite = signal<PerformanceReview[] | null>(null);
  protected readonly asked = signal<ReviewFeedback[]>([]);
  /** The signed-in employee's running improvement plan, if any. */
  protected readonly myPlan = signal<ImprovementPlan | null>(null);
  /** Whether the signed-in user runs any improvement plan (or is HR seeing them all). */
  protected readonly runsPlans = signal(false);
  /** Requests to answer first, then answered or closed ones. */
  protected readonly feedbackQueue = computed(() =>
    [...this.asked()].sort(
      (a, b) => Number(this.toAnswer(b)) - Number(this.toAnswer(a)) || (a.dueDate ?? '').localeCompare(b.dueDate ?? ''),
    ),
  );
  protected readonly error = signal<string | null>(null);
  protected readonly filters: QueueFilter[] = ['todo', 'done', 'all'];
  protected readonly filter = signal<QueueFilter>('todo');

  protected readonly counts = computed<Record<QueueFilter, number>>(() => {
    const list = this.toWrite() ?? [];
    return { todo: list.filter(r => this.needsMe(r)).length, done: list.filter(r => this.isDone(r)).length, all: list.length };
  });
  protected readonly queue = computed(() => {
    const list = this.toWrite() ?? [];
    const f = this.filter();
    const visible = f === 'all' ? list : list.filter(r => (f === 'todo' ? this.needsMe(r) : this.isDone(r)));
    return [...visible].sort((a, b) => (a.employeeName ?? '').localeCompare(b.employeeName ?? ''));
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.mine.set(null);
    this.toWrite.set(null);
    // Improvement plans are secondary here: a failure leaves the page as it was.
    forkJoin([this.planService.mine(), this.planService.managed()]).subscribe({
      next: ([mine, managed]) => {
        this.myPlan.set(mine.find(p => p.status === 'ACTIVE') ?? null);
        this.runsPlans.set(managed.length > 0);
      },
      error: () => undefined,
    });
    forkJoin([this.reviewCycleService.myReviews(), this.reviewCycleService.reviewsToWrite(), this.feedbackService.mine()]).subscribe({
      next: ([mine, toWrite, asked]) => {
        this.mine.set(mine);
        this.toWrite.set(toWrite);
        this.asked.set(asked);
        if (!this.counts().todo && toWrite.length) this.filter.set('all');
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.mine.set([]);
        this.toWrite.set([]);
      },
    });
  }

  /** What the employee does or waits for next on their own review. */
  protected nextForMe(r: PerformanceReview): string {
    if (this.toAcknowledge(r)) return this.label('humano.performance.list.mine.acknowledge');
    if (r.status === 'SHARED') return this.label('humano.performance.list.mine.shared');
    if (r.status === 'MANAGER_SUBMITTED') return this.label('humano.performance.list.mine.meeting', { name: r.reviewerName });
    const selfOpen = r.cyclePhase === 'SELF_ASSESSMENT' || r.cyclePhase === 'MANAGER_REVIEW';
    if (r.status === 'NOT_STARTED') {
      return this.label(selfOpen ? 'humano.performance.list.mine.write' : 'humano.performance.list.mine.notOpen');
    }
    return r.reviewerName
      ? this.label('humano.performance.list.mine.waiting', { name: r.reviewerName })
      : this.label('humano.performance.list.mine.waitingUnassigned');
  }

  /** The employee has something to do on their own review: the self-assessment, or acknowledging it once shared. */
  protected mineNeedsMe(r: PerformanceReview): boolean {
    return r.status === 'NOT_STARTED' || this.toAcknowledge(r);
  }

  /** The reviewer still has something to do on this review in the current phase. */
  protected needsMe(r: PerformanceReview): boolean {
    if (returned(r)) return true;
    if (r.cyclePhase === 'MANAGER_REVIEW') return r.status !== 'MANAGER_SUBMITTED' && r.status !== 'SHARED';
    if (r.cyclePhase === 'FEEDBACK_DELIVERY') return r.status === 'MANAGER_SUBMITTED' && r.signOff?.status !== 'PENDING';
    return false;
  }

  /**
   * The reviewer's part is over: the review is shared, or written and waiting for the next phase.
   * A review neither to do nor done (manager reviews not open yet, or missed) shows under All only.
   */
  protected isDone(r: PerformanceReview): boolean {
    return r.status === 'SHARED' || (r.status === 'MANAGER_SUBMITTED' && r.cyclePhase !== 'FEEDBACK_DELIVERY' && !returned(r));
  }

  /** What the reviewer does or waits for next. */
  protected nextForReviewer(r: PerformanceReview): string {
    if (r.status === 'SHARED') return this.label('humano.performance.list.queue.shared');
    if (r.status === 'MANAGER_SUBMITTED') {
      if (returned(r)) return this.label('humano.performance.list.queue.returned');
      if (r.signOff?.status === 'PENDING') return this.label('humano.performance.list.queue.signOff');
      if (r.cyclePhase === 'FEEDBACK_DELIVERY') return this.label('humano.performance.list.queue.meeting');
      if (r.cyclePhase === 'CALIBRATION') return this.label('humano.performance.list.queue.calibrating');
      return this.label('humano.performance.list.queue.written');
    }
    if (r.cyclePhase === 'MANAGER_REVIEW') return this.label('humano.performance.list.queue.write');
    if (r.cyclePhase === 'SELF_ASSESSMENT') return this.label('humano.performance.list.queue.opensLater');
    return this.label('humano.performance.list.queue.missed');
  }

  /** A feedback request still waiting for the signed-in employee's answer. */
  protected toAnswer(f: ReviewFeedback): boolean {
    return f.open && f.status === 'REQUESTED';
  }

  /** Where a feedback request stands, for its contributor. */
  protected feedbackState(f: ReviewFeedback): string {
    if (this.toAnswer(f)) return this.label('humano.performance.list.asked.toGive');
    if (f.status === 'SUBMITTED') {
      return this.label(f.open ? 'humano.performance.list.asked.givenEditable' : 'humano.performance.list.asked.given');
    }
    return this.label('humano.performance.list.asked.closed');
  }

  private toAcknowledge(r: PerformanceReview): boolean {
    return r.status === 'SHARED' && !r.acknowledgement && r.cyclePhase !== 'ARCHIVED';
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}

/** Returned at sign-off: back with the reviewer to revise and send again. */
function returned(r: PerformanceReview): boolean {
  return r.signOff?.status === 'RETURNED';
}
