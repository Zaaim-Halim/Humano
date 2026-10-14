import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  SkeletonRowComponent,
  TrendChartComponent,
  TrendPoint,
} from 'app/shared/ui';

import { EmployeeTraining } from '../models/employee-training.model';
import { PerformanceReview } from '../models/performance-review.model';
import { EmployeeTrainingService } from '../services/employee-training.service';
import { MeWorkService } from '../services/me-work.service';
import { PerformanceReviewService } from '../services/performance-review.service';

/**
 * Performance & growth for one employee.
 *
 * - Figures: latest rating (with the change since the previous review), average rating, number
 *   of reviews and trainings completed.
 * - The latest review in full (stars, reviewer, comments), a rating trend chart (1–5), and every
 *   earlier review on a timeline.
 * - Learning: training enrolments with status, completion date and feedback (hidden when the
 *   viewer may not read trainings).
 */
@Component({
  selector: 'hum-employee-performance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
    TrendChartComponent,
  ],
  templateUrl: './employee-performance.component.html',
})
export class EmployeePerformanceComponent {
  readonly employeeId = input.required<string>();
  /** Read the signed-in employee's own data via `/api/me/**` (My profile). */
  readonly self = input(false);

  private readonly reviewService = inject(PerformanceReviewService);
  private readonly trainingService = inject(EmployeeTrainingService);
  private readonly meWork = inject(MeWorkService);
  private readonly translate = inject(TranslateService);

  /** Newest first. */
  protected readonly reviews = signal<PerformanceReview[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly trainings = signal<EmployeeTraining[]>([]);
  protected readonly stars = [1, 2, 3, 4, 5];

  private readonly rated = computed(() => (this.reviews() ?? []).filter(r => r.rating !== null));
  protected readonly latest = computed(() => this.reviews()?.[0] ?? null);
  protected readonly earlier = computed(() => (this.reviews() ?? []).slice(1));
  protected readonly stats = computed(() => {
    const rated = this.rated();
    const [last, prev] = rated;
    return {
      average: rated.length ? rated.reduce((s, r) => s + (r.rating ?? 0), 0) / rated.length : null,
      delta: last && prev ? (last.rating ?? 0) - (prev.rating ?? 0) : null,
      count: this.reviews()?.length ?? 0,
      completed: this.trainings().filter(t => t.status === 'COMPLETED').length,
      planned: this.trainings().filter(t => t.status === 'PLANNED').length,
    };
  });
  protected readonly trend = computed<TrendPoint[]>(() =>
    [...this.rated()].reverse().map(r => ({
      label: this.half(r.reviewDate),
      value: r.rating ?? 0,
      annotation: String(r.rating),
      title: `${r.reviewDate}: ${r.rating}/5${r.reviewerName ? ` — ${r.reviewerName}` : ''}`,
    })),
  );

  constructor() {
    effect(() => {
      const id = this.employeeId();
      untracked(() => this.load(id));
    });
  }

  protected load(id = this.employeeId()): void {
    this.error.set(null);
    const page = { page: 0, size: 50, sort: ['reviewDate,desc'] };
    const reviews$ = this.self() ? this.meWork.performanceReviews(page) : this.reviewService.search({ employeeId: id }, page);
    const trainings$ = this.self()
      ? this.meWork.trainings({ page: 0, size: 50 })
      : this.trainingService.forEmployee(id, { page: 0, size: 50 });
    reviews$.subscribe({
      next: page => this.reviews.set(page.content),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.reviews.set([]);
      },
    });
    trainings$.subscribe({
      next: page =>
        this.trainings.set([...page.content].sort((a, b) => (b.completionDate ?? '9999').localeCompare(a.completionDate ?? '9999'))),
      error: () => this.trainings.set([]),
    });
  }

  protected ratingLabel(rating: number | null): string {
    return rating ? this.t(`humano.insights.perf.rating.${rating}`) : '—';
  }

  /** "H1 2025" for a review date. */
  private half(isoDate: string): string {
    const d = new Date(`${isoDate}T00:00:00`);
    return `H${d.getMonth() < 6 ? 1 : 2} ${d.getFullYear()}`;
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
