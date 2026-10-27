import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  FormFieldComponent,
  SkeletonComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { ReviewFeedback } from '../models/review-feedback.model';
import { ReviewFeedbackService } from '../services/review-feedback.service';

const TEXT_MAX = 10000;

/**
 * A colleague's feedback on someone's review — `/reviews/feedback/:requestId`, for the contributor
 * only. They see who it's for, who asked and why, and who will read their answer; they write
 * strengths and areas to improve (one of the two is needed) and may revise it until the review is
 * written. They may also decline, saying why.
 */
@Component({
  selector: 'hum-review-feedback',
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
    DialogComponent,
    FormFieldComponent,
    SkeletonComponent,
    TextareaComponent,
  ],
  templateUrl: './review-feedback.component.html',
})
export default class ReviewFeedbackComponent {
  /** Route param. */
  readonly requestId = input.required<string>();

  private readonly feedbackService = inject(ReviewFeedbackService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly request = signal<ReviewFeedback | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  /** The form shows while nothing is given yet, or after "Edit". */
  protected readonly editing = signal(false);
  protected readonly showForm = computed(() => {
    const r = this.request();
    return !!r && r.open && (this.editing() || r.status === 'REQUESTED');
  });
  protected readonly form = this.fb.nonNullable.group({
    strengths: ['', Validators.maxLength(TEXT_MAX)],
    improvements: ['', Validators.maxLength(TEXT_MAX)],
    comments: ['', Validators.maxLength(TEXT_MAX)],
  });
  /** Strengths or improvements: the server refuses feedback with neither. */
  protected readonly empty = signal(false);

  protected readonly declineOpen = signal(false);
  protected readonly declineReason = signal('');

  constructor() {
    toObservable(this.requestId)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected load(id = this.requestId()): void {
    this.error.set(null);
    this.feedbackService.find(id).subscribe({
      next: r => this.apply(r),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected edit(): void {
    const r = this.request();
    this.form.reset({ strengths: r?.strengths ?? '', improvements: r?.improvements ?? '', comments: r?.comments ?? '' });
    this.editing.set(true);
  }

  protected submit(): void {
    const r = this.request();
    if (!r?.id) return;
    const v = this.form.getRawValue();
    if (!v.strengths.trim() && !v.improvements.trim()) {
      this.empty.set(true);
      return;
    }
    if (this.form.invalid) return;
    const wasGiven = r.status === 'SUBMITTED';
    this.saving.set(true);
    this.feedbackService
      .submit(r.id, {
        strengths: v.strengths.trim() || null,
        improvements: v.improvements.trim() || null,
        comments: v.comments.trim() || null,
      })
      .subscribe({
        next: updated => {
          this.apply(updated);
          this.saving.set(false);
          this.toast.success(
            this.label(wasGiven ? 'humano.performance.give.updated' : 'humano.performance.give.sent', { name: updated.employeeName }),
          );
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  protected openDecline(): void {
    this.declineReason.set('');
    this.declineOpen.set(true);
  }

  protected decline(): void {
    const r = this.request();
    const reason = this.declineReason().trim();
    if (!r?.id || !reason) return;
    this.saving.set(true);
    this.feedbackService.decline(r.id, reason).subscribe({
      next: updated => {
        this.apply(updated);
        this.saving.set(false);
        this.declineOpen.set(false);
        this.toast.success(this.label('humano.performance.give.declined'));
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.toast.danger(normalizeHttpError(err));
      },
    });
  }

  private apply(r: ReviewFeedback): void {
    this.request.set(r);
    this.editing.set(false);
    this.empty.set(false);
    this.form.reset({ strengths: r.strengths ?? '', improvements: r.improvements ?? '', comments: r.comments ?? '' });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
