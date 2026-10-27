import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { REVIEW_CYCLE_TYPES, ReviewCycleType } from 'app/features/employee';
import {
  AlertComponent,
  ButtonComponent,
  EmptyStateComponent,
  FormFieldComponent,
  PageHeaderComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  StatTileComponent,
} from 'app/shared/ui';

import { CycleStats, PerformanceAnalytics, PerformanceStats } from './performance-analytics.model';
import { PerformanceAnalyticsService } from './performance-analytics.service';

const RATINGS = [1, 2, 3, 4, 5];

/**
 * Performance analytics — `/performance-analytics` — for HR with `VIEW_PERFORMANCE_REVIEWS`, over the
 * latest cycles of one type:
 *
 * - the selected cycle: completion, the final rating distribution, calibration movement, and
 *   promotion, improvement-plan and dispute rates;
 * - the trend across cycles;
 * - the selected cycle per department (organisation structure only — never demographics).
 *
 * Every rate shows its base: completion over all reviews, acknowledgement and disputes over shared
 * ones, the rest over rated reviews. One hue throughout; numbers sit next to every bar.
 */
@Component({
  selector: 'hum-performance-analytics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    EmptyStateComponent,
    FormFieldComponent,
    PageHeaderComponent,
    SelectComponent,
    SkeletonComponent,
    StatTileComponent,
  ],
  templateUrl: './performance-analytics.component.html',
})
export default class PerformanceAnalyticsComponent {
  private readonly service = inject(PerformanceAnalyticsService);
  private readonly translate = inject(TranslateService);

  protected readonly ratings = RATINGS;
  protected readonly type = signal<ReviewCycleType>('REGULAR');
  protected readonly limit = signal(6);
  protected readonly data = signal<PerformanceAnalytics | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedId = signal<string | null>(null);

  protected readonly typeOptions = computed<SelectOption[]>(() =>
    REVIEW_CYCLE_TYPES.map(t => ({ value: t, label: this.label(`humano.performance.cycleType.${t}`) })),
  );
  protected readonly limitOptions = computed<SelectOption[]>(() =>
    [3, 6, 12].map(n => ({ value: `${n}`, label: this.label('humano.performanceAnalytics.lastCycles', { count: n }) })),
  );
  protected readonly cycleOptions = computed<SelectOption[]>(() =>
    (this.data()?.cycles ?? []).map(c => ({ value: c.cycleId, label: c.name })),
  );
  protected readonly selected = computed<CycleStats | null>(() => {
    const cycles = this.data()?.cycles ?? [];
    return cycles.find(c => c.cycleId === this.selectedId()) ?? (cycles.length ? cycles[0] : null);
  });
  /** Oldest first, so the trend reads left to right in time. */
  protected readonly trend = computed(() => [...(this.data()?.cycles ?? [])].reverse());

  constructor() {
    effect(() => {
      const type = this.type();
      const limit = this.limit();
      untracked(() => this.load(type, limit));
    });
  }

  protected load(type = this.type(), limit = this.limit()): void {
    this.error.set(null);
    this.data.set(null);
    this.service.analytics(type, limit).subscribe({
      next: d => {
        this.data.set(d);
        if (!d.cycles.some(c => c.cycleId === this.selectedId())) {
          this.selectedId.set(null);
        }
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  /** `part` of `base` in whole percent; null without a base. */
  protected pct(part: number, base: number): number | null {
    return base > 0 ? Math.round((part * 100) / base) : null;
  }

  /** Each rating's share of the rated reviews, for bar widths. */
  protected shares(s: PerformanceStats): number[] {
    return s.distribution.map(n => (s.rated > 0 ? (n * 100) / s.rated : 0));
  }

  /** Bar heights for a mini histogram, scaled to its tallest bar. */
  protected heights(s: PerformanceStats): number[] {
    const max = Math.max(...s.distribution, 1);
    return s.distribution.map(n => (n * 100) / max);
  }

  protected above(s: PerformanceStats): number | null {
    return this.pct(s.distribution[3] + s.distribution[4], s.rated);
  }

  protected below(s: PerformanceStats): number | null {
    return this.pct(s.distribution[0] + s.distribution[1], s.rated);
  }

  protected tile(part: number, base: number): string {
    const p = this.pct(part, base);
    return p === null ? '—' : `${p}%`;
  }

  protected of(part: number, base: number): string {
    return this.label('humano.performanceAnalytics.of', { part, base });
  }

  protected barTitle(rating: number, count: number, s: PerformanceStats): string {
    return this.label('humano.performanceAnalytics.barTitle', {
      rating,
      label: this.label(`humano.performance.rating.${rating}`),
      count,
      share: this.pct(count, s.rated) ?? 0,
    });
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
