import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, map, tap } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  CurrentEmployeeService,
  EmployeeService,
  PerformanceReview,
  PerformanceReviewStatus,
  RATING_SCALE,
  ReviewCycle,
  ReviewCycleEmployee,
  ReviewCyclePhase,
  ReviewCycleService,
  ReviewFeedbackCount,
  ReviewFeedbackService,
  ReviewPhaseStep,
} from 'app/features/employee';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  ProgressComponent,
  SkeletonComponent,
  SkeletonRowComponent,
  StepperComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { daysBetween, localIsoDate } from '../models/onboarding.model';
import { ReviewCycleFormComponent } from '../review-cycles/review-cycle-form.component';
import { cyclePhases, phaseDeadline } from '../review-cycles/review-cycle.util';

type Filter = 'all' | 'unassigned' | 'disputed' | 'signOff' | PerformanceReviewStatus;
const FILTERS: Filter[] = [
  'all',
  'NOT_STARTED',
  'SELF_SUBMITTED',
  'MANAGER_SUBMITTED',
  'SHARED',
  'unassigned',
  'disputed',
  'signOff',
  'EXCLUDED',
];
/** Phases in which reviews are worked on: HR can take one out of the cycle or put it back. */
const RUNNING: readonly ReviewCyclePhase[] = ['SELF_ASSESSMENT', 'MANAGER_REVIEW', 'CALIBRATION', 'FEEDBACK_DELIVERY', 'GOAL_SETTING'];
const REASON_MAX = 500;

/** A deadline the "go back a phase" dialog asks for, as the request names it. */
type DeadlineField = 'selfAssessmentDeadline' | 'managerReviewDeadline' | 'calibrationDeadline' | 'feedbackDeadline';
const DEADLINES: { field: DeadlineField; phase: ReviewCyclePhase; label: string }[] = [
  { field: 'selfAssessmentDeadline', phase: 'SELF_ASSESSMENT', label: 'self' },
  { field: 'managerReviewDeadline', phase: 'MANAGER_REVIEW', label: 'manager' },
  { field: 'calibrationDeadline', phase: 'CALIBRATION', label: 'calibration' },
  { field: 'feedbackDeadline', phase: 'FEEDBACK_DELIVERY', label: 'feedback' },
];
/** Largest cycle the page lists in full. */
const PAGE = 1000;

/** The next step HR takes from each phase: what it's called, its endpoint and the i18n key of its confirmation. */
interface NextStep {
  key: string;
  icon: string;
  run: (cycle: ReviewCycle) => Observable<ReviewCycle>;
}

/**
 * One review cycle — `/review-cycles/:id` (`VIEW_PERFORMANCE_REVIEWS`).
 *
 * - Header: name, period under review, scope, phase, the phase track, and the next step with
 *   `CONFIGURE_PERFORMANCE_CYCLES` — edit the draft, open the self-assessment (which creates the
 *   reviews and notifies everyone), start manager reviews, calibration when the cycle has it,
 *   feedback meetings, close (sharing finished reviews not shared yet), archive — each confirmed
 *   first, plus reminders to whoever still has something to do in the current phase.
 * - Figures: people in review, self-assessments, manager reviews, shared reviews and reviews the
 *   employee acknowledged; disputes waiting for HR are flagged and filterable, and so are reviews
 *   waiting for their sign-off or returned at it, in a cycle requiring one.
 * - Reviews: everyone in the cycle with their reviewer, status and final rating (marked when
 *   calibrated), filtered by status or "no reviewer". HR with `MANAGE_PERFORMANCE_REVIEWS`
 *   assigns or changes reviewers until the review is written; a row opens the review, where
 *   calibration happens.
 *   Each row also shows colleagues' feedback received out of requested.
 * - Side: key dates, colleagues' feedback (the cycle's settings and totals), rating distribution
 *   (for calibration) and progress per department.
 * - Membership, with `CONFIGURE_PERFORMANCE_CYCLES` while the cycle runs: take a review out of the
 *   cycle (with a reason; never one's own or a shared one) and put it back; add people who joined
 *   late until manager reviews end; and move a phase started too early back, re-entering its
 *   deadlines. Excluded reviews stay listed, under their own filter, and count nowhere else.
 */
@Component({
  selector: 'hum-review-cycle-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AutocompleteComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    EmptyStateComponent,
    FormFieldComponent,
    InputComponent,
    ProgressComponent,
    SkeletonComponent,
    SkeletonRowComponent,
    StepperComponent,
    TextareaComponent,
    ReviewCycleFormComponent,
  ],
  templateUrl: './review-cycle-detail.component.html',
})
export default class ReviewCycleDetailComponent {
  /** Route param. */
  readonly id = input.required<string>();

  private readonly reviewCycleService = inject(ReviewCycleService);
  private readonly feedbackService = inject(ReviewFeedbackService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);
  private readonly me = inject(CurrentEmployeeService).currentEmployeeId;

  protected readonly canConfigure = this.account.hasPermission(Permission.CONFIGURE_PERFORMANCE_CYCLES);
  protected readonly canAssign =
    this.account.hasPermission(Permission.MANAGE_PERFORMANCE_REVIEWS) && this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();
  protected readonly filters = FILTERS;
  protected readonly scale = RATING_SCALE;

  protected readonly cycle = signal<ReviewCycle | null>(null);
  protected readonly reviews = signal<PerformanceReview[] | null>(null);
  /** Colleagues' feedback per review id; reviews without requests are absent. */
  protected readonly feedback = signal<ReadonlyMap<string, ReviewFeedbackCount>>(new Map());
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal<Filter>('all');
  protected readonly search = signal('');
  protected readonly busy = signal(false);

  protected readonly phases = computed(() => {
    const c = this.cycle();
    return c ? cyclePhases(c) : [];
  });
  protected readonly steps = computed(() => this.phases().map(p => this.label(`humano.performance.phaseShort.${p}`)));
  protected readonly step = computed(() => {
    const c = this.cycle();
    if (!c) return 0;
    if (c.phase === 'ARCHIVED') return this.phases().length;
    const i = this.phases().indexOf(c.phase);
    return c.phase === 'COMPLETED' ? this.phases().length : Math.max(0, i);
  });
  protected readonly deadline = computed(() => {
    const c = this.cycle();
    return c ? phaseDeadline(c) : null;
  });
  protected readonly daysLeft = computed(() => {
    const d = this.deadline();
    return d ? daysBetween(this.today, d) : null;
  });

  /** HR's next step from the current phase. */
  protected readonly next = computed<NextStep | null>(() => {
    const c = this.cycle();
    if (!c || !this.canConfigure) return null;
    const phase = (step: ReviewPhaseStep, key: string, icon: string): NextStep => ({
      key,
      icon,
      run: cycle => this.reviewCycleService.startPhase(cycle.cycleId, step),
    });
    switch (c.phase) {
      case 'DRAFT':
        return phase('self-assessment', 'openSelf', 'play');
      case 'SELF_ASSESSMENT':
        return phase('manager-review', 'startManager', 'arrow-right');
      case 'MANAGER_REVIEW':
        return c.deadlines.calibrationDeadline
          ? phase('calibration', 'startCalibration', 'sliders-horizontal')
          : phase('feedback', 'startFeedback', 'message-square');
      case 'CALIBRATION':
        return phase('feedback', 'startFeedback', 'message-square');
      case 'FEEDBACK_DELIVERY':
      case 'GOAL_SETTING':
        return { key: 'close', icon: 'check-circle-2', run: cycle => this.reviewCycleService.close(cycle.cycleId) };
      case 'COMPLETED':
        return { key: 'archive', icon: 'archive', run: cycle => this.reviewCycleService.archive(cycle.cycleId) };
      default:
        return null;
    }
  });
  /** Instead of closing after feedback meetings: a goal-setting phase where reviewers agree next period's goals. */
  protected readonly alternative = computed<NextStep | null>(() =>
    this.canConfigure && this.cycle()?.phase === 'FEEDBACK_DELIVERY'
      ? { key: 'startGoalSetting', icon: 'target', run: cycle => this.reviewCycleService.startPhase(cycle.cycleId, 'goal-setting') }
      : null,
  );
  protected readonly canRemind = computed(() => {
    const p = this.cycle()?.phase;
    return this.canConfigure && (p === 'SELF_ASSESSMENT' || p === 'MANAGER_REVIEW' || p === 'FEEDBACK_DELIVERY' || p === 'GOAL_SETTING');
  });
  /** HR takes reviews out of a running cycle and puts them back. */
  protected readonly membershipOpen = computed(() => {
    const p = this.cycle()?.phase;
    return this.canConfigure && !!p && RUNNING.includes(p);
  });
  /** People are added until manager reviews end. */
  protected readonly canAddPeople = computed(() => {
    const p = this.cycle()?.phase;
    return this.canConfigure && (p === 'SELF_ASSESSMENT' || p === 'MANAGER_REVIEW');
  });
  /** The phase going back reopens, as the server works it out; `null` where there is none. */
  protected readonly previousPhase = computed<ReviewCyclePhase | null>(() =>
    this.canConfigure ? (this.cycle()?.previousPhase ?? null) : null,
  );
  /** Reviewers can be changed until the review is written, in the self-assessment and manager review phases. */
  protected readonly reviewersOpen = computed(() => {
    const p = this.cycle()?.phase;
    return this.canAssign && (p === 'SELF_ASSESSMENT' || p === 'MANAGER_REVIEW');
  });

  protected readonly kpis = computed(() => {
    const p = this.cycle()?.progress;
    if (!p) return [];
    return [
      { key: 'self', done: p.completedSelfAssessments },
      { key: 'manager', done: p.completedManagerReviews },
      { key: 'shared', done: p.deliveredFeedbacks },
      { key: 'acknowledged', done: p.acknowledged ?? 0 },
    ].map(k => ({ ...k, pct: p.totalEmployees ? Math.round((k.done / p.totalEmployees) * 100) : 0 }));
  });
  protected readonly counts = computed(() => {
    const counts: Record<Filter, number> = {
      all: 0,
      unassigned: 0,
      disputed: 0,
      signOff: 0,
      NOT_STARTED: 0,
      SELF_SUBMITTED: 0,
      MANAGER_SUBMITTED: 0,
      SHARED: 0,
      EXCLUDED: 0,
    };
    for (const r of this.reviews() ?? []) {
      counts.all++;
      counts[r.status]++;
      if (needsReviewer(r)) counts.unassigned++;
      if (disputeOpen(r)) counts.disputed++;
      if (signOffOpen(r)) counts.signOff++;
    }
    return counts;
  });
  protected readonly visible = computed(() => {
    const f = this.filter();
    const term = this.search().trim().toLowerCase();
    return (this.reviews() ?? [])
      .filter(r => f === 'all' || matches(r, f))
      .filter(
        r =>
          !term || [r.employeeName, r.reviewerName, r.departmentName, r.employeeJobTitle].some(v => (v ?? '').toLowerCase().includes(term)),
      )
      .sort((a, b) => (a.employeeName ?? '').localeCompare(b.employeeName ?? ''));
  });
  /** Final ratings given so far, per rating — what calibration looks at. */
  protected readonly distribution = computed(() => {
    const rated = (this.reviews() ?? []).filter(r => r.rating != null && r.status !== 'EXCLUDED');
    const max = Math.max(1, ...RATING_SCALE.map(n => rated.filter(r => r.rating === n).length));
    return {
      total: rated.length,
      bars: RATING_SCALE.map(n => {
        const count = rated.filter(r => r.rating === n).length;
        return { rating: n, count, pct: (count / max) * 100, share: rated.length ? Math.round((count / rated.length) * 100) : 0 };
      }),
    };
  });

  // Dialogs.
  /** The step being confirmed: the next one or the alternative. */
  protected readonly chosen = signal<NextStep | null>(null);
  protected readonly formOpen = signal(false);
  protected readonly assigning = signal<PerformanceReview | null>(null);
  protected readonly reviewerId = signal('');
  protected readonly searchEmployees = (term: string): Observable<AutocompleteOption[]> => this.employeeService.searchOptions(term);
  protected readonly reasonMax = REASON_MAX;
  protected readonly excluding = signal<PerformanceReview | null>(null);
  protected readonly reason = signal('');
  protected readonly adding = signal(false);
  protected readonly people = signal<ReviewCycleEmployee[]>([]);
  /** Bumped after each pick, so the search box starts empty again. */
  protected readonly pickerKey = signal(0);
  private readonly seen = new Map<string, string>();
  protected readonly searchAddable = (term: string): Observable<AutocompleteOption[]> =>
    this.reviewCycleService.addableEmployees(this.id(), term).pipe(
      map(list =>
        list
          .filter(e => !this.people().some(p => p.id === e.id))
          .map(e => ({ value: e.id, label: e.name, sublabel: e.jobTitle ?? e.departmentName })),
      ),
      tap(options => options.forEach(o => this.seen.set(o.value, o.label))),
    );
  protected readonly movingBack = signal(false);
  /** The deadlines going back sets: the reopened phase's and the later ones the cycle has, prefilled. */
  protected readonly backDeadlines = signal<{ field: DeadlineField; label: string; value: string }[]>([]);

  constructor() {
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    this.reviewCycleService.find(id).subscribe({
      next: c => {
        this.cycle.set(c);
        this.loadReviews(c);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected isCalibrated(r: PerformanceReview): boolean {
    const own = r.managerAssessment?.rating;
    return own != null && r.rating != null && own !== r.rating;
  }

  protected runChosen(): void {
    const c = this.cycle();
    const step = this.chosen();
    if (!c || !step) return;
    this.busy.set(true);
    step.run(c).subscribe({
      next: updated => {
        this.busy.set(false);
        this.chosen.set(null);
        this.cycle.set(updated);
        this.loadReviews(updated);
        this.toast.success(this.label(`humano.performance.cycle.next.${step.key}.done`, { name: updated.name }));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected remind(): void {
    const c = this.cycle();
    if (!c) return;
    this.busy.set(true);
    this.reviewCycleService.remind(c.cycleId).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(this.label('humano.performance.cycle.reminded'));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected edited(cycle: ReviewCycle): void {
    this.formOpen.set(false);
    this.cycle.set(cycle);
  }

  protected openAssign(r: PerformanceReview, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.reviewerId.set(r.reviewerId ?? '');
    this.assigning.set(r);
  }

  protected assign(): void {
    const r = this.assigning();
    const c = this.cycle();
    const reviewerId = this.reviewerId();
    if (!r || !c || !reviewerId) return;
    this.busy.set(true);
    this.reviewCycleService.assignReviewer(c.cycleId, r.id, reviewerId).subscribe({
      next: updated => {
        this.busy.set(false);
        this.assigning.set(null);
        this.reviews.update(list => (list ?? []).map(x => (x.id === updated.id ? updated : x)));
        // A reassigned review asks the previous reviewer for feedback.
        this.loadFeedback(c);
        this.toast.success(this.label('humano.performance.cycle.assigned', { name: updated.reviewerName, employee: updated.employeeName }));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  /** Never on one's own review, never once shared: the server refuses both. */
  protected canExclude(r: PerformanceReview): boolean {
    return this.membershipOpen() && r.status !== 'EXCLUDED' && r.status !== 'SHARED' && r.employeeId !== this.me();
  }

  protected openExclude(r: PerformanceReview): void {
    this.reason.set('');
    this.excluding.set(r);
  }

  protected exclude(): void {
    const r = this.excluding();
    const c = this.cycle();
    const reason = this.reason().trim();
    if (!r || !c || !reason || reason.length > REASON_MAX) return;
    this.busy.set(true);
    this.reviewCycleService.exclude(c.cycleId, r.id, reason).subscribe({
      next: updated => {
        this.excluding.set(null);
        this.membershipChanged(c, updated);
        this.toast.success(this.label('humano.performance.cycle.membership.excluded', { name: updated.employeeName }));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected restore(r: PerformanceReview): void {
    const c = this.cycle();
    if (!c) return;
    this.busy.set(true);
    this.reviewCycleService.restore(c.cycleId, r.id).subscribe({
      next: updated => {
        this.membershipChanged(c, updated);
        this.toast.success(this.label('humano.performance.cycle.membership.restored', { name: updated.employeeName }));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected openAdd(): void {
    this.people.set([]);
    this.adding.set(true);
  }

  protected addPerson(id: string | null): void {
    if (!id || this.people().some(p => p.id === id)) return;
    this.people.update(list => [...list, { id, name: this.seen.get(id) ?? id }]);
    this.pickerKey.update(k => k + 1);
  }

  protected removePerson(id: string): void {
    this.people.update(list => list.filter(p => p.id !== id));
  }

  protected addPeople(): void {
    const c = this.cycle();
    const ids = this.people().map(p => p.id);
    if (!c || !ids.length) return;
    this.busy.set(true);
    this.reviewCycleService.addEmployees(c.cycleId, ids).subscribe({
      next: added => {
        this.busy.set(false);
        this.adding.set(false);
        this.toast.success(this.label('humano.performance.cycle.membership.added', { count: added.length }));
        this.load(c.cycleId);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected openMoveBack(): void {
    const c = this.cycle();
    const to = this.previousPhase();
    if (!c || !to) return;
    const from = DEADLINES.findIndex(d => d.phase === to);
    this.backDeadlines.set(
      DEADLINES.slice(from)
        .filter(d => d.field !== 'calibrationDeadline' || !!c.deadlines.calibrationDeadline)
        .map(d => ({ field: d.field, label: d.label, value: c.deadlines[d.field] ?? '' })),
    );
    this.reason.set('');
    this.movingBack.set(true);
  }

  protected setBackDeadline(field: DeadlineField, value: string): void {
    this.backDeadlines.update(list => list.map(d => (d.field === field ? { ...d, value } : d)));
  }

  protected moveBack(): void {
    const c = this.cycle();
    const reason = this.reason().trim();
    if (!c || !reason || reason.length > REASON_MAX) return;
    const deadlines = Object.fromEntries(this.backDeadlines().map(d => [d.field, d.value || null]));
    this.busy.set(true);
    this.reviewCycleService.moveBack(c.cycleId, { reason, ...deadlines }).subscribe({
      next: updated => {
        this.busy.set(false);
        this.movingBack.set(false);
        this.cycle.set(updated);
        this.loadReviews(updated);
        this.toast.success(
          this.label('humano.performance.cycle.moveBack.done', { phase: this.label(`humano.performance.phaseShort.${updated.phase}`) }),
        );
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  /** An ad hoc cycle's people, listed in the hero. */
  protected employeeNames(c: ReviewCycle): string {
    return c.employees.map(e => e.name).join(', ');
  }

  /** A review left or rejoined the cycle: its row changes, and so do the cycle's figures. */
  private membershipChanged(c: ReviewCycle, updated: PerformanceReview): void {
    this.busy.set(false);
    this.reviews.update(list => (list ?? []).map(x => (x.id === updated.id ? updated : x)));
    this.loadFeedback(c);
    this.reviewCycleService.find(c.cycleId).subscribe({ next: fresh => this.cycle.set(fresh) });
  }

  private loadReviews(c: ReviewCycle): void {
    if (c.phase === 'DRAFT') {
      this.reviews.set([]);
      this.feedback.set(new Map());
      return;
    }
    this.loadFeedback(c);
    this.reviews.set(null);
    this.reviewCycleService.reviews(c.cycleId, undefined, { page: 0, size: PAGE }).subscribe({
      next: page => this.reviews.set(page.content),
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.reviews.set([]);
      },
    });
  }

  private loadFeedback(c: ReviewCycle): void {
    this.feedbackService.counts(c.cycleId).subscribe({
      next: counts => this.feedback.set(new Map(counts.map(f => [f.reviewId, f]))),
      error: () => this.feedback.set(new Map()),
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}

function matches(r: PerformanceReview, f: Filter): boolean {
  switch (f) {
    case 'unassigned':
      return needsReviewer(r);
    case 'disputed':
      return disputeOpen(r);
    case 'signOff':
      return signOffOpen(r);
    default:
      return r.status === f;
  }
}

/** Excluded reviews need nobody: they aren't written. */
function needsReviewer(r: PerformanceReview): boolean {
  return !r.reviewerId && r.status !== 'EXCLUDED';
}

/** Waiting for its sign-off, or returned to the reviewer: it can't be calibrated or shared yet. */
function signOffOpen(r: PerformanceReview): boolean {
  return r.signOff?.status === 'PENDING' || r.signOff?.status === 'RETURNED';
}

/** The employee disputes the review and HR hasn't resolved it yet. */
function disputeOpen(r: PerformanceReview): boolean {
  return !!r.acknowledgement?.disputed && !r.acknowledgement.resolvedAt;
}
