import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { distinctUntilChanged, filter, switchMap } from 'rxjs';
import { DatePipe, DOCUMENT } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DialogComponent,
  FormFieldComponent,
  ProgressComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  StepperComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import {
  PerformanceReview,
  PerformanceReviewStatus,
  PROBATION_RECOMMENDATIONS,
  ProbationRecommendation,
  REVIEW_POTENTIALS,
  ReviewPotential,
  ReviewSignOffDetail,
} from '../models/performance-review.model';
import { GOAL_ACHIEVEMENT_SCORE, GOAL_ACHIEVEMENTS, GoalAchievement } from '../models/goal.model';
import { ReviewFormCompetency, ReviewFormQuestion } from '../models/performance-framework.model';
import { RATING_SCALE, RatingChange } from '../models/review-cycle.model';
import { ReviewImprovementPlanStatus } from '../models/improvement-plan.model';
import { CurrentEmployeeService } from '../services/current-employee.service';
import { ImprovementPlanService } from '../services/improvement-plan.service';
import { ImprovementPlanFormComponent } from '../improvement-plans/improvement-plan-form.component';
import { ReviewCycleService } from '../services/review-cycle.service';
import { ReviewFeedbackPanelComponent } from './review-feedback-panel.component';

const TEXT_MAX = 10000;
/** An excluded review stays where it was: see `step`. */
const STEP_INDEX: Record<PerformanceReviewStatus, number> = {
  NOT_STARTED: 0,
  SELF_SUBMITTED: 1,
  MANAGER_SUBMITTED: 2,
  SHARED: 3,
  EXCLUDED: 0,
};

/**
 * One review in a cycle — `/reviews/:id` — for the three people who work on it:
 *
 * - The employee writes their self-assessment (achievements, challenges, development goals)
 *   during the self-assessment and manager review phases, until their reviewer has written the
 *   review. The reviewer's side appears once the review is shared, without the reviewer's own
 *   rating before calibration, the promotion recommendation or the calibration note.
 * - The reviewer (or HR with `MANAGE_PERFORMANCE_REVIEWS`) reads the self-assessment, writes the
 *   review during the manager review phase — a rating on the five-point scale, strengths, areas
 *   to improve, development, overall comments and an optional promotion recommendation — and
 *   records the feedback meeting, which shares it with the employee.
 * - HR sees all of it; with `MANAGE_CALIBRATION` they adjust the rating during calibration, with
 *   a reason when it changes.
 * - Colleagues' feedback sits between the self-assessment and the review (see
 *   {@link ReviewFeedbackPanelComponent}).
 * - Once shared, the employee acknowledges the review — read, not necessarily agreed — with an
 *   optional comment, or disputes it with a reason. HR with `MANAGE_PERFORMANCE_REVIEWS` (neither
 *   the employee nor the reviewer) resolves a dispute, possibly changing the rating; HR sees every
 *   change to the final rating after the reviewer gave it.
 * - In a cycle requiring sign-off, the written review waits for the reviewer's manager (or the
 *   approval chain), who reads it here and signs it off or returns it with a comment. Until it is
 *   signed off it can't be calibrated or shared; a returned review is revised and sent again. The
 *   employee never sees the sign-off.
 *
 * Nobody acts on their own review, whatever their rights.
 */
@Component({
  selector: 'hum-performance-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    CheckboxComponent,
    DialogComponent,
    FormFieldComponent,
    SelectComponent,
    ProgressComponent,
    SkeletonComponent,
    SkeletonRowComponent,
    StepperComponent,
    TextareaComponent,
    ReviewFeedbackPanelComponent,
    ImprovementPlanFormComponent,
  ],
  templateUrl: './performance-review.component.html',
})
export default class PerformanceReviewComponent {
  /** Route param. */
  readonly id = input.required<string>();

  private readonly reviewCycleService = inject(ReviewCycleService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly planService = inject(ImprovementPlanService);
  private readonly router = inject(Router);
  private readonly account = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  private readonly document = inject(DOCUMENT);

  protected readonly scale = RATING_SCALE;
  protected readonly canManage = this.account.hasPermission(Permission.MANAGE_PERFORMANCE_REVIEWS);
  protected readonly canCalibrate = this.account.hasPermission(Permission.MANAGE_CALIBRATION);
  protected readonly canSeeCycles = this.account.hasPermission(Permission.VIEW_PERFORMANCE_REVIEWS);

  protected readonly review = signal<PerformanceReview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  private readonly me = this.currentEmployee.currentEmployeeId;
  /** No form until we know who is signed in: before that, the employee would look like a stranger to their own review. */
  protected readonly known = this.currentEmployee.resolved;
  protected readonly isEmployee = computed(() => !!this.review() && this.review()!.employeeId === this.me());
  protected readonly isReviewer = computed(() => !!this.review()?.reviewerId && this.review()!.reviewerId === this.me());
  /** Writes the reviewer's side: the reviewer, or HR managing reviews — never on their own review. */
  private readonly actsAsReviewer = computed(() => this.known() && !this.isEmployee() && (this.isReviewer() || this.canManage));

  protected readonly canWriteSelf = computed(() => {
    const r = this.review();
    return (
      !!r &&
      this.known() &&
      this.isEmployee() &&
      (r.cyclePhase === 'SELF_ASSESSMENT' || r.cyclePhase === 'MANAGER_REVIEW') &&
      (r.status === 'NOT_STARTED' || r.status === 'SELF_SUBMITTED')
    );
  });
  /** During manager review — and, returned at sign-off, until the cycle closes. */
  protected readonly canWriteReview = computed(() => {
    const r = this.review();
    const open =
      r?.cyclePhase === 'MANAGER_REVIEW' ||
      (r?.signOff?.status === 'RETURNED' &&
        (r.cyclePhase === 'CALIBRATION' || r.cyclePhase === 'FEEDBACK_DELIVERY' || r.cyclePhase === 'GOAL_SETTING'));
    return !!r && this.actsAsReviewer() && open && !!r.reviewerId && r.status !== 'SHARED' && r.status !== 'EXCLUDED';
  });
  protected readonly canRecordMeeting = computed(() => {
    const r = this.review();
    return (
      !!r &&
      this.actsAsReviewer() &&
      (r.cyclePhase === 'FEEDBACK_DELIVERY' || r.cyclePhase === 'GOAL_SETTING') &&
      (r.status === 'MANAGER_SUBMITTED' || r.status === 'SHARED') &&
      !r.acknowledgement &&
      signedOff(r)
    );
  });
  protected readonly canAcknowledge = computed(() => {
    const r = this.review();
    return !!r && this.known() && this.isEmployee() && r.status === 'SHARED' && !r.acknowledgement && r.cyclePhase !== 'ARCHIVED';
  });
  /** HR outside the dispute: never the employee or the reviewer. */
  protected readonly canResolve = computed(() => {
    const a = this.review()?.acknowledgement;
    return this.known() && this.canManage && !this.isEmployee() && !this.isReviewer() && !!a?.disputed && !a.resolvedAt;
  });
  protected readonly canCalibrateNow = computed(() => {
    const r = this.review();
    return (
      !!r && !this.isEmployee() && this.canCalibrate && r.cyclePhase === 'CALIBRATION' && r.status === 'MANAGER_SUBMITTED' && signedOff(r)
    );
  });

  protected readonly steps = computed(() =>
    ['self', 'manager', 'shared', 'acknowledged'].map(s => this.label(`humano.performance.review.step.${s}`)),
  );
  protected readonly step = computed(() => {
    const r = this.review();
    if (r?.status === 'EXCLUDED') return r.managerAssessment ? 2 : r.selfAssessment ? 1 : 0;
    return r?.acknowledgement ? 4 : STEP_INDEX[r?.status ?? 'NOT_STARTED'];
  });
  /** The final rating differs from the reviewer's: it was calibrated (never shown to the employee). */
  protected readonly calibrated = computed(() => {
    const r = this.review();
    const own = r?.managerAssessment?.rating;
    return own != null && r?.rating != null && own !== r.rating;
  });
  /** Where "back" goes: the cycle for HR looking at someone else's review, otherwise the review list. */
  protected readonly backLink = computed(() => {
    const r = this.review();
    return r?.cycleId && this.canSeeCycles && !this.isEmployee() && !this.isReviewer() ? ['/review-cycles', r.cycleId] : ['/reviews'];
  });

  // Self-assessment. The form shows while nothing is written yet, or after "Edit".
  protected readonly editingSelf = signal(false);
  protected readonly showSelfForm = computed(() => this.canWriteSelf() && (this.editingSelf() || !this.review()?.selfAssessment));
  protected readonly selfForm = this.fb.nonNullable.group({
    achievements: ['', [Validators.required, Validators.maxLength(TEXT_MAX)]],
    challenges: ['', Validators.maxLength(TEXT_MAX)],
    developmentGoals: ['', Validators.maxLength(TEXT_MAX)],
    additionalComments: ['', Validators.maxLength(TEXT_MAX)],
  });

  // Manager review, likewise.
  protected readonly editingReview = signal(false);
  /** Opens by itself for the reviewer only; HR writing on the reviewer's behalf asks for it. */
  protected readonly showReviewForm = computed(
    () => this.canWriteReview() && (this.editingReview() || (this.isReviewer() && !this.review()?.managerAssessment)),
  );
  protected readonly reviewForm = this.fb.group({
    rating: this.fb.control<number | null>(null, Validators.required),
    strengths: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(TEXT_MAX)]),
    areasForImprovement: this.fb.nonNullable.control('', Validators.maxLength(TEXT_MAX)),
    developmentRecommendations: this.fb.nonNullable.control('', Validators.maxLength(TEXT_MAX)),
    managerComments: this.fb.nonNullable.control('', Validators.maxLength(TEXT_MAX)),
    recommendForPromotion: this.fb.nonNullable.control(false),
    promotionJustification: this.fb.nonNullable.control('', Validators.maxLength(TEXT_MAX)),
    potential: this.fb.nonNullable.control<ReviewPotential | ''>(''),
    probationRecommendation: this.fb.nonNullable.control<ProbationRecommendation | ''>(''),
  });
  protected readonly probationOptions: SelectOption[] = [
    { value: '', label: this.label('humano.employeeForm.select') },
    ...PROBATION_RECOMMENDATIONS.map(p => ({ value: p, label: this.label(`humano.performance.probation.${p}`) })),
  ];
  /** For a shared review rated 2 or lower: whether an improvement plan can start, or the one that did. */
  protected readonly planStatus = signal<ReviewImprovementPlanStatus | null>(null);
  protected readonly planFormOpen = signal(false);

  // Beyond the standard form: the cycle template's competencies and questions, and the goals the reviewer rates.
  protected readonly form = computed(() => this.review()?.form ?? null);
  protected readonly employeeQuestions = computed(() => (this.form()?.questions ?? []).filter(q => q.audience === 'EMPLOYEE'));
  protected readonly reviewerQuestions = computed(() => (this.form()?.questions ?? []).filter(q => q.audience === 'REVIEWER'));
  /** Answers being written, by question id. */
  protected readonly answerDraft = signal<Record<string, string | undefined>>({});
  /** Competency ratings being given, by competency id. */
  protected readonly ratingDraft = signal<Record<string, { rating: number | null; comment: string } | undefined>>({});
  protected readonly selfFormMissing = computed(() => this.employeeQuestions().some(q => this.answerMissing(q)));
  /** Goal ratings being given, by goal id. */
  protected readonly goalDraft = signal<Record<string, { achievement: GoalAchievement | null; comment: string } | undefined>>({});
  protected readonly goals = computed(() => this.form()?.goals ?? []);
  protected readonly goalAchievements = GOAL_ACHIEVEMENTS;
  protected readonly reviewFormMissing = computed(
    () =>
      (this.form()?.competencies ?? []).some(c => this.ratingDraft()[c.id]?.rating == null) ||
      this.reviewerQuestions().some(q => this.answerMissing(q)) ||
      this.goals().some(g => !this.goalDraft()[g.id]?.achievement),
  );
  /** Total weight of the review's goals, when they have weights; flagged unless it is 100. */
  protected readonly goalWeightTotal = computed(() => {
    const weighted = this.goals().filter(g => g.weight != null);
    return weighted.length ? weighted.reduce((sum, g) => sum + (g.weight ?? 0), 0) : null;
  });
  /**
   * Weighted goal achievement in percent (Met = 100), as guidance for the overall rating — never the
   * rating itself. Uses what is drafted while writing, what was saved otherwise.
   */
  protected readonly goalScore = computed(() => {
    const goals = this.goals();
    const achieved = goals.map(g => ({ g, a: this.goalDraft()[g.id]?.achievement ?? g.achievement }));
    if (!goals.length || achieved.some(x => !x.a)) return null;
    const weighted = goals.every(g => g.weight != null) && goals.some(g => (g.weight ?? 0) > 0);
    const total = achieved.reduce((sum, x) => sum + (weighted ? x.g.weight! : 1), 0);
    const score = achieved.reduce((sum, x) => sum + GOAL_ACHIEVEMENT_SCORE[x.a!] * (weighted ? x.g.weight! : 1), 0);
    return Math.round(score / total);
  });

  protected readonly potentialOptions: SelectOption[] = [
    { value: '', label: this.label('humano.performance.manager.potentialNone') },
    ...REVIEW_POTENTIALS.map(p => ({ value: p, label: this.label(`humano.performance.potential.${p}`) })),
  ];
  private readonly reviewValue = toSignal(this.reviewForm.valueChanges, { initialValue: this.reviewForm.getRawValue() });
  protected readonly recommending = computed(() => this.reviewValue().recommendForPromotion === true);
  protected readonly justificationMissing = computed(
    () => this.recommending() && !(this.reviewValue().promotionJustification ?? '').trim(),
  );
  /** A probation review needs the reviewer's recommendation: confirm, extend or not confirm. */
  protected readonly isProbation = computed(() => this.review()?.cycleType === 'PROBATION');
  protected readonly probationMissing = computed(() => this.isProbation() && !this.reviewValue().probationRecommendation);

  // Feedback meeting.
  protected readonly meetingOpen = signal(false);
  protected readonly meetingNotes = signal('');

  // Calibration.
  protected readonly calibrationOpen = signal(false);
  protected readonly calibrationRating = signal<number | null>(null);
  protected readonly calibrationNote = signal('');
  protected readonly calibrationPotential = signal<ReviewPotential | ''>('');
  protected readonly ratingOptions: SelectOption[] = RATING_SCALE.map(r => ({
    value: String(r),
    label: `${r} — ${this.label(`humano.performance.rating.${r}`)}`,
  }));
  protected readonly calibrationNeedsNote = computed(() => {
    const own = this.review()?.managerAssessment?.rating;
    return this.calibrationRating() !== null && this.calibrationRating() !== own && !this.calibrationNote().trim();
  });

  // Acknowledgement.
  protected readonly ackComment = signal('');
  protected readonly ackDisputed = signal(false);
  protected readonly ackReasonMissing = computed(() => this.ackDisputed() && !this.ackComment().trim());

  // Dispute resolution.
  protected readonly resolveOpen = signal(false);
  protected readonly resolution = signal('');
  protected readonly resolveRating = signal<number | null>(null);

  // Sign-off: where it stands and on whom it waits; never for the employee.
  protected readonly signOff = signal<ReviewSignOffDetail | null>(null);
  private readonly signOffKey = computed(() => {
    const r = this.review();
    return r?.signOff && this.known() && !this.isEmployee() ? `${r.id}|${r.signOff.status}|${r.signOff.decidedAt ?? ''}` : null;
  });
  protected readonly decision = signal<'APPROVE' | 'REJECT' | null>(null);
  protected readonly decisionComment = signal('');

  /** Changes to the final rating, for HR looking at someone else's review. */
  protected readonly ratingHistory = signal<RatingChange[]>([]);
  private readonly historyKey = computed(() => {
    const r = this.review();
    return r && this.known() && this.canSeeCycles && !this.isEmployee() ? `${r.id}|${r.rating}` : null;
  });

  protected readonly downloading = signal(false);

  constructor() {
    this.currentEmployee.resolve();
    toObservable(this.historyKey)
      .pipe(
        distinctUntilChanged(),
        filter((key): key is string => key !== null),
        switchMap(() => this.reviewCycleService.ratingChanges(this.review()!.id)),
        takeUntilDestroyed(),
      )
      .subscribe({ next: changes => this.ratingHistory.set(changes), error: () => this.ratingHistory.set([]) });
    toObservable(this.signOffKey)
      .pipe(
        distinctUntilChanged(),
        filter((key): key is string => key !== null),
        switchMap(() => this.reviewCycleService.signOff(this.review()!.id)),
        takeUntilDestroyed(),
      )
      .subscribe({ next: detail => this.signOff.set(detail), error: () => this.signOff.set(null) });
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    this.reviewCycleService.review(id).subscribe({
      next: r => this.apply(r),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected ratingLabel(rating: number | null | undefined): string {
    return rating ? this.label(`humano.performance.rating.${rating}`) : '';
  }

  protected setRating(rating: number): void {
    this.reviewForm.controls.rating.setValue(rating);
    this.reviewForm.controls.rating.markAsDirty();
  }

  protected invalid(form: 'self' | 'review', control: string): boolean {
    const c = form === 'self' ? this.selfForm.get(control) : this.reviewForm.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected answerMissing(q: ReviewFormQuestion): boolean {
    return q.required && !(this.answerDraft()[q.id] ?? '').trim();
  }

  protected setAnswer(questionId: string, value: string): void {
    this.answerDraft.update(d => ({ ...d, [questionId]: value }));
  }

  protected setCompetencyRating(competencyId: string, rating: number): void {
    this.ratingDraft.update(d => ({ ...d, [competencyId]: { comment: d[competencyId]?.comment ?? '', rating } }));
  }

  protected setCompetencyComment(competencyId: string, comment: string): void {
    this.ratingDraft.update(d => ({ ...d, [competencyId]: { rating: d[competencyId]?.rating ?? null, comment } }));
  }

  protected setGoalAchievement(goalId: string, achievement: GoalAchievement): void {
    this.goalDraft.update(d => ({ ...d, [goalId]: { comment: d[goalId]?.comment ?? '', achievement } }));
  }

  protected setGoalComment(goalId: string, comment: string): void {
    this.goalDraft.update(d => ({ ...d, [goalId]: { achievement: d[goalId]?.achievement ?? null, comment } }));
  }

  protected goalTone(a: GoalAchievement | null): 'success' | 'warning' | 'danger' | 'brand' | 'neutral' {
    switch (a) {
      case 'EXCEEDED':
        return 'brand';
      case 'MET':
        return 'success';
      case 'PARTIALLY_MET':
        return 'warning';
      case 'NOT_MET':
        return 'danger';
      default:
        return 'neutral';
    }
  }

  /** What the given level looks like for a competency; blank when the framework doesn't say. */
  protected anchor(c: ReviewFormCompetency, rating: number | null | undefined): string | null {
    return rating ? (c.anchors[rating - 1] ?? null) : null;
  }

  /** The saved answer to a question, for reading. */
  protected answerOf(questionId: string): string | null {
    return this.form()?.answers.find(a => a.questionId === questionId)?.answer ?? null;
  }

  /** The saved rating of a competency, for reading. */
  protected ratingOf(competencyId: string): { rating: number; comment: string | null } | null {
    return this.form()?.ratings.find(r => r.competencyId === competencyId) ?? null;
  }

  protected editSelf(): void {
    const s = this.review()?.selfAssessment;
    this.selfForm.reset({
      achievements: s?.achievements ?? '',
      challenges: s?.challenges ?? '',
      developmentGoals: s?.developmentGoals ?? '',
      additionalComments: s?.comments ?? '',
    });
    this.seedForm(this.review());
    this.editingSelf.set(true);
  }

  protected submitSelf(): void {
    const r = this.review();
    if (!r?.cycleId) return;
    if (this.selfForm.invalid || this.selfFormMissing()) {
      this.selfForm.markAllAsTouched();
      return;
    }
    const v = this.selfForm.getRawValue();
    const answers = this.answersFor(this.employeeQuestions());
    this.run(
      this.reviewCycleService.submitSelfAssessment(r.cycleId, {
        achievements: v.achievements.trim(),
        ...optional('challenges', v.challenges),
        ...optional('developmentGoals', v.developmentGoals),
        ...optional('additionalComments', v.additionalComments),
        ...(answers.length ? { answers } : {}),
      }),
      'humano.performance.review.selfSubmitted',
    );
  }

  protected editReview(): void {
    const m = this.review()?.managerAssessment;
    this.reviewForm.reset({
      rating: m?.rating ?? null,
      strengths: m?.strengths ?? '',
      areasForImprovement: m?.areasForImprovement ?? '',
      developmentRecommendations: m?.developmentRecommendations ?? '',
      managerComments: this.review()?.comments ?? '',
      recommendForPromotion: m?.recommendForPromotion ?? false,
      promotionJustification: m?.promotionJustification ?? '',
      potential: m?.potential ?? '',
      probationRecommendation: m?.probationRecommendation ?? '',
    });
    this.seedForm(this.review());
    this.editingReview.set(true);
  }

  protected submitReview(): void {
    const r = this.review();
    if (!r?.cycleId) return;
    if (this.reviewForm.invalid || this.justificationMissing() || this.probationMissing() || this.reviewFormMissing()) {
      this.reviewForm.markAllAsTouched();
      return;
    }
    const v = this.reviewForm.getRawValue();
    const answers = this.answersFor(this.reviewerQuestions());
    const ratings = (this.form()?.competencies ?? []).map(c => {
      const d = this.ratingDraft()[c.id];
      return { competencyId: c.id, rating: d!.rating!, ...optional('comment', d?.comment ?? '') };
    });
    const goalRatings = this.goals().map(g => {
      const d = this.goalDraft()[g.id];
      return { goalId: g.id, achievement: d!.achievement!, ...optional('comment', d?.comment ?? '') };
    });
    this.run(
      this.reviewCycleService.submitManagerReview(r.cycleId, {
        employeeId: r.employeeId,
        rating: v.rating!,
        strengths: v.strengths.trim(),
        recommendForPromotion: v.recommendForPromotion,
        ...optional('areasForImprovement', v.areasForImprovement),
        ...optional('developmentRecommendations', v.developmentRecommendations),
        ...optional('managerComments', v.managerComments),
        ...(v.recommendForPromotion ? optional('promotionJustification', v.promotionJustification) : {}),
        ...(v.potential ? { potential: v.potential } : {}),
        ...(this.isProbation() && v.probationRecommendation ? { probationRecommendation: v.probationRecommendation } : {}),
        ...(ratings.length ? { competencyRatings: ratings } : {}),
        ...(answers.length ? { answers } : {}),
        ...(goalRatings.length ? { goalRatings } : {}),
      }),
      'humano.performance.review.reviewSubmitted',
    );
  }

  protected openMeeting(): void {
    this.meetingNotes.set(this.review()?.feedbackNotes ?? '');
    this.meetingOpen.set(true);
  }

  protected recordMeeting(): void {
    const r = this.review();
    if (!r?.cycleId) return;
    const wasShared = r.status === 'SHARED';
    this.run(
      this.reviewCycleService.recordFeedbackMeeting(r.cycleId, r.employeeId, this.meetingNotes().trim() || undefined),
      wasShared ? 'humano.performance.review.notesSaved' : 'humano.performance.review.shared',
      () => this.meetingOpen.set(false),
    );
  }

  protected openCalibration(): void {
    this.calibrationRating.set(this.review()?.rating ?? null);
    this.calibrationNote.set(this.review()?.managerAssessment?.calibrationNote ?? '');
    this.calibrationPotential.set(this.review()?.managerAssessment?.potential ?? '');
    this.calibrationOpen.set(true);
  }

  protected calibrate(): void {
    const r = this.review();
    const rating = this.calibrationRating();
    if (!r?.cycleId || rating === null || this.calibrationNeedsNote()) return;
    const note = this.calibrationNote().trim();
    const potential = this.calibrationPotential();
    this.run(
      this.reviewCycleService.calibrate(r.cycleId, r.id, { rating, ...(note ? { note } : {}), ...(potential ? { potential } : {}) }),
      'humano.performance.review.calibrated',
      () => this.calibrationOpen.set(false),
    );
  }

  protected acknowledge(): void {
    const r = this.review();
    if (!r || this.ackReasonMissing()) return;
    const comment = this.ackComment().trim();
    const disputed = this.ackDisputed();
    this.run(
      this.reviewCycleService.acknowledge(r.id, { disputed, ...(comment ? { comment } : {}) }),
      disputed ? 'humano.performance.review.disputeSent' : 'humano.performance.review.acknowledged',
    );
  }

  protected openResolve(): void {
    this.resolution.set('');
    this.resolveRating.set(this.review()?.rating ?? null);
    this.resolveOpen.set(true);
  }

  protected resolve(): void {
    const r = this.review();
    const resolution = this.resolution().trim();
    if (!r || !resolution) return;
    const rating = this.resolveRating();
    this.run(
      this.reviewCycleService.resolveDispute(r.id, { resolution, ...(rating !== null ? { rating } : {}) }),
      'humano.performance.review.disputeResolved',
      () => this.resolveOpen.set(false),
    );
  }

  protected openDecision(decision: 'APPROVE' | 'REJECT'): void {
    this.decisionComment.set('');
    this.decision.set(decision);
  }

  protected decide(): void {
    const r = this.review();
    const decision = this.decision();
    const approvalId = this.signOff()?.approvalRequestId;
    const comment = this.decisionComment().trim();
    if (!r || !decision || !approvalId || (decision === 'REJECT' && !comment)) return;
    this.saving.set(true);
    this.reviewCycleService.decideSignOff(approvalId, decision, comment || undefined).subscribe({
      next: () => {
        this.saving.set(false);
        this.decision.set(null);
        this.toast.success(
          this.label(decision === 'APPROVE' ? 'humano.performance.signOff.approved' : 'humano.performance.signOff.returned', {
            name: r.reviewerName,
          }),
        );
        this.load(r.id);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  protected planStarted(plan: { id: string }): void {
    this.planFormOpen.set(false);
    this.toast.success(this.label('humano.improvementPlans.drafted'));
    void this.router.navigate(['/improvement-plans', plan.id]);
  }

  /** Downloads the shared review as a PDF — the employee's view, whoever asks. */
  protected downloadPdf(): void {
    const r = this.review();
    if (!r) return;
    this.downloading.set(true);
    this.reviewCycleService.reviewPdf(r.id).subscribe({
      next: res => {
        this.downloading.set(false);
        if (!res.body) return;
        const url = URL.createObjectURL(res.body);
        const a = this.document.createElement('a');
        a.href = url;
        a.download = filenameOf(res.headers.get('Content-Disposition')) ?? 'performance-review.pdf';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err: unknown) => {
        this.downloading.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  /** Starts the template drafts from what was saved. */
  private seedForm(r: PerformanceReview | null): void {
    const answers: Record<string, string> = {};
    r?.form?.answers.forEach(a => (answers[a.questionId] = a.answer));
    const ratings: Record<string, { rating: number | null; comment: string }> = {};
    r?.form?.ratings.forEach(x => (ratings[x.competencyId] = { rating: x.rating, comment: x.comment ?? '' }));
    const goals: Record<string, { achievement: GoalAchievement | null; comment: string }> = {};
    r?.form?.goals.forEach(g => (goals[g.id] = { achievement: g.achievement, comment: g.comment ?? '' }));
    this.answerDraft.set(answers);
    this.ratingDraft.set(ratings);
    this.goalDraft.set(goals);
  }

  /** The non-blank drafted answers to `questions`, trimmed. */
  private answersFor(questions: ReviewFormQuestion[]): { questionId: string; answer: string }[] {
    return questions.map(q => ({ questionId: q.id, answer: (this.answerDraft()[q.id] ?? '').trim() })).filter(a => a.answer.length > 0);
  }

  private run(request$: ReturnType<ReviewCycleService['review']>, successKey: string, then?: () => void): void {
    this.saving.set(true);
    request$.subscribe({
      next: r => {
        this.apply(r);
        this.saving.set(false);
        then?.();
        this.toast.success(this.label(successKey, { name: r.employeeName }));
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  private apply(r: PerformanceReview): void {
    this.review.set(r);
    this.seedForm(r);
    this.editingSelf.set(false);
    this.editingReview.set(false);
    this.loadPlanStatus(r);
  }

  /** A shared review rated 2 or lower may lead to an improvement plan: whether one can start, or the one that did. */
  private loadPlanStatus(r: PerformanceReview): void {
    this.planStatus.set(null);
    if (r.status !== 'SHARED' || r.rating == null || r.rating > 2 || !r.cycleId) return;
    this.planService.forReview(r.id).subscribe({
      next: status => this.planStatus.set(status),
      error: () => this.planStatus.set(null),
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}

/** No sign-off needed, or signed off or waived: the review may be calibrated and shared. */
function signedOff(r: PerformanceReview): boolean {
  return !r.signOff || r.signOff.status === 'APPROVED' || r.signOff.status === 'WAIVED';
}

/** `{ [key]: value }` for a non-blank value, `{}` otherwise — optional fields are left out, not sent blank. */
function optional<K extends string>(key: K, value: string): Partial<Record<K, string>> {
  const trimmed = value.trim();
  return trimmed ? ({ [key]: trimmed } as Record<K, string>) : {};
}

/** The file name from a `Content-Disposition` header, if any. */
function filenameOf(header: string | null): string | null {
  const match = header ? /filename="([^"]+)"/.exec(header) : null;
  return match ? match[1] : null;
}
