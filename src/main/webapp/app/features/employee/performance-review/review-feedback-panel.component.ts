import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable, distinctUntilChanged } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AutocompleteComponent,
  AutocompleteOption,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { PerformanceReview } from '../models/performance-review.model';
import { FEEDBACK_RELATIONSHIPS, FeedbackRelationship, ReviewFeedback, ReviewFeedbackOverview } from '../models/review-feedback.model';
import { ReviewFeedbackService } from '../services/review-feedback.service';

/**
 * "Feedback from colleagues" on a review page: who was asked, what they said, and the actions the
 * viewer may take — all decided by the server ({@link ReviewFeedbackOverview}).
 *
 * - The reviewer (or HR managing reviews) asks colleagues directly, approves or turns down the
 *   employee's nominations, cancels unanswered requests, and reads every answer with names.
 * - The reviewed employee nominates colleagues when the cycle allows it, withdraws nominations not
 *   yet approved, and sees the answers only once the review is shared — and only if the cycle
 *   shares them (named, or anonymous from three answers).
 *
 * Hidden when there is nothing to show or do.
 */
@Component({
  selector: 'hum-review-feedback-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AutocompleteComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SkeletonRowComponent,
    TextareaComponent,
  ],
  templateUrl: './review-feedback-panel.component.html',
})
export class ReviewFeedbackPanelComponent {
  readonly review = input.required<PerformanceReview>();
  /** The viewer is the reviewed employee. */
  readonly isEmployee = input(false);

  private readonly feedbackService = inject(ReviewFeedbackService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly overview = signal<ReviewFeedbackOverview | null>(null);
  protected readonly error = signal<string | null>(null);
  /** The request an action is running on. */
  protected readonly busy = signal<string | null>(null);

  protected readonly visible = computed(() => {
    const o = this.overview();
    return !!o && (o.canRequest || o.canNominate || o.requests.length > 0 || o.withheld);
  });
  protected readonly named = computed(() => (this.overview()?.requests ?? []).filter(r => r.id !== null));
  protected readonly anonymous = computed(() => (this.overview()?.requests ?? []).filter(r => r.id === null));
  protected readonly full = computed(() => {
    const o = this.overview();
    return !!o && o.activeContributors >= o.maxContributors;
  });
  /** What the lead says about who sees the answers, from the viewer's side. */
  protected readonly visibilityKey = computed(() => {
    const o = this.overview();
    if (!o) return '';
    const side = this.isEmployee() ? 'employee' : 'reviewer';
    return `humano.performance.feedback.visibility.${side}.${o.visibility}`;
  });

  // Asking (or nominating).
  protected readonly askOpen = signal(false);
  protected readonly contributorId = signal('');
  protected readonly relationship = signal<FeedbackRelationship>('PEER');
  protected readonly dueDate = signal('');
  protected readonly message = signal('');
  protected readonly relationshipOptions: SelectOption[] = FEEDBACK_RELATIONSHIPS.map(r => ({
    value: r,
    label: this.label(`humano.performance.feedback.relationship.${r}`),
  }));
  protected readonly searchColleagues = (term: string): Observable<AutocompleteOption[]> => {
    const r = this.review();
    return this.feedbackService.candidates(r.cycleId!, r.id, term);
  };

  // Turning a nomination down.
  protected readonly declining = signal<ReviewFeedback | null>(null);
  protected readonly declineReason = signal('');

  constructor() {
    // Reload when the review changes stage: what the viewer may see and do depends on it.
    toObservable(computed(() => `${this.review().id}|${this.review().status}|${this.review().cyclePhase}`))
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load());
  }

  protected load(): void {
    const r = this.review();
    if (!r.cycleId) return;
    this.error.set(null);
    this.feedbackService.forReview(r.cycleId, r.id).subscribe({
      next: o => this.overview.set(o),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected openAsk(): void {
    this.contributorId.set('');
    this.relationship.set('PEER');
    this.dueDate.set('');
    this.message.set('');
    this.askOpen.set(true);
  }

  protected ask(): void {
    const r = this.review();
    if (!r.cycleId || !this.contributorId()) return;
    const message = this.message().trim();
    this.busy.set('new');
    this.feedbackService
      .request(r.cycleId, r.id, {
        contributorId: this.contributorId(),
        relationship: this.relationship(),
        ...(this.dueDate() ? { dueDate: this.dueDate() } : {}),
        ...(message ? { message } : {}),
      })
      .subscribe({
        next: created => {
          this.askOpen.set(false);
          this.done(
            created.status === 'PENDING_APPROVAL' ? 'humano.performance.feedback.nominated' : 'humano.performance.feedback.asked',
            created,
          );
        },
        error: (err: unknown) => this.failed(err),
      });
  }

  protected approve(request: ReviewFeedback): void {
    this.act(request, this.feedbackService.approve(request.id!), 'humano.performance.feedback.approved');
  }

  protected cancel(request: ReviewFeedback): void {
    this.act(
      request,
      this.feedbackService.cancel(request.id!),
      this.isEmployee() ? 'humano.performance.feedback.withdrawn' : 'humano.performance.feedback.cancelled',
    );
  }

  protected openDecline(request: ReviewFeedback): void {
    this.declineReason.set('');
    this.declining.set(request);
  }

  protected decline(): void {
    const request = this.declining();
    if (!request) return;
    this.act(
      request,
      this.feedbackService.decline(request.id!, this.declineReason().trim() || null),
      'humano.performance.feedback.turnedDown',
      () => this.declining.set(null),
    );
  }

  /** The employee may withdraw their own nomination while it awaits approval. */
  protected canWithdraw(request: ReviewFeedback): boolean {
    return this.isEmployee() && request.status === 'PENDING_APPROVAL' && request.requestedById === this.review().employeeId;
  }

  protected nominatedByEmployee(request: ReviewFeedback): boolean {
    return !!request.requestedById && request.requestedById === this.review().employeeId;
  }

  protected relationshipLabel(request: ReviewFeedback): string {
    return request.relationship ? this.label(`humano.performance.feedback.relationship.${request.relationship}`) : '';
  }

  private act(request: ReviewFeedback, action$: Observable<ReviewFeedback>, successKey: string, then?: () => void): void {
    this.busy.set(request.id);
    action$.subscribe({
      next: updated => {
        then?.();
        this.done(successKey, updated);
      },
      error: (err: unknown) => this.failed(err),
    });
  }

  private done(successKey: string, request: ReviewFeedback): void {
    this.busy.set(null);
    this.toast.success(this.label(successKey, { name: request.contributorName }));
    this.load();
  }

  private failed(err: unknown): void {
    this.busy.set(null);
    this.toast.danger(normalizeHttpError(err));
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
