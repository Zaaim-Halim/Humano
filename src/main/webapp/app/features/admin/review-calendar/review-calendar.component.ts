import { ChangeDetectionStrategy, Component, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { DatePipe, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { ReviewCadence, ReviewCalendar, ReviewCalendarService, UpdateReviewCalendarRequest } from 'app/features/employee';
import {
  AlertComponent,
  ButtonComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SwitchComponent,
  ToastService,
} from 'app/shared/ui';

/** Phase lengths in display order, with the server's limits (calibration 0 = none). */
const PHASES = [
  { key: 'selfAssessmentDays', label: 'self', min: 1, max: 90 },
  { key: 'managerReviewDays', label: 'manager', min: 1, max: 90 },
  { key: 'calibrationDays', label: 'calibration', min: 0, max: 60 },
  { key: 'feedbackDays', label: 'feedback', min: 1, max: 90 },
] as const;
const CADENCES: ReviewCadence[] = ['ANNUAL', 'SEMI_ANNUAL'];
/** A calibration length to restore when calibration is switched back on. */
const DEFAULT_CALIBRATION_DAYS = 7;

/**
 * Review calendar — `/review-cycles/calendar` (`CONFIGURE_PERFORMANCE_CYCLES`): when the company
 * reviews and how long each step takes.
 *
 * - When: once a year or twice (mid-year and year-end), the month the review year starts (for a
 *   fiscal year), and how many days before the period ends the cycle opens.
 * - How long: the self-assessment, manager review, calibration (switched off = no calibration)
 *   and feedback meetings, previewed as one plan.
 * - The next cycle the calendar proposes, which pre-fills "New cycle". Nothing starts by itself:
 *   HR still creates and opens every cycle, and saving doesn't change existing cycles.
 */
@Component({
  selector: 'hum-review-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    InputComponent,
    SelectComponent,
    SkeletonComponent,
    SwitchComponent,
  ],
  templateUrl: './review-calendar.component.html',
})
export default class ReviewCalendarComponent {
  private readonly calendarService = inject(ReviewCalendarService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly locale = inject(LOCALE_ID);

  protected readonly phases = PHASES;
  protected readonly cadences = CADENCES;
  protected readonly monthOptions: SelectOption[] = Array.from({ length: 12 }, (_, i) => ({
    value: String(i + 1),
    label: formatDate(new Date(2000, i, 1), 'MMMM', this.locale),
  }));

  protected readonly loaded = signal<ReviewCalendar | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly draft = signal<UpdateReviewCalendarRequest | null>(null);
  private readonly baseline = signal('');

  protected readonly dirty = computed(() => JSON.stringify(this.draft()) !== this.baseline());
  protected readonly calibrates = computed(() => (this.draft()?.calibrationDays ?? 0) > 0);
  protected readonly errors = computed((): Record<string, boolean> => {
    const d = this.draft();
    if (!d) return {};
    const bad = (v: number, min: number, max: number): boolean => !Number.isInteger(v) || v < min || v > max;
    const out: Record<string, boolean> = { opensDaysBeforePeriodEnd: bad(d.opensDaysBeforePeriodEnd, 0, 90) };
    for (const p of PHASES) out[p.key] = bad(d[p.key], p.key === 'calibrationDays' && !this.calibrates() ? 0 : Math.max(1, p.min), p.max);
    return out;
  });
  protected readonly valid = computed(() => !Object.values(this.errors()).some(Boolean));
  /** The phases as one plan: each one's share of the whole, and the total in days. */
  protected readonly plan = computed(() => {
    const d = this.draft();
    if (!d) return { total: 0, segments: [] };
    const segments = PHASES.filter(p => p.key !== 'calibrationDays' || this.calibrates()).map(p => ({
      label: p.label,
      days: Math.max(0, Math.round(d[p.key]) || 0),
    }));
    const total = segments.reduce((sum, s) => sum + s.days, 0);
    return { total, segments: segments.map(s => ({ ...s, pct: total ? (s.days / total) * 100 : 0 })) };
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.calendarService.get().subscribe({
      next: c => this.apply(c),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected set<K extends keyof UpdateReviewCalendarRequest>(key: K, value: UpdateReviewCalendarRequest[K]): void {
    this.draft.update(d => (d ? { ...d, [key]: value } : d));
  }

  protected setCalibration(on: boolean): void {
    this.set('calibrationDays', on ? DEFAULT_CALIBRATION_DAYS : 0);
  }

  protected discard(): void {
    const c = this.loaded();
    if (c) this.apply(c);
  }

  protected save(): void {
    const d = this.draft();
    if (!d || !this.valid()) return;
    this.saving.set(true);
    this.calendarService.save(d).subscribe({
      next: c => {
        this.apply(c);
        this.saving.set(false);
        this.toast.success(this.label('humano.performance.calendar.saved'));
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  private apply(c: ReviewCalendar): void {
    this.loaded.set(c);
    const draft: UpdateReviewCalendarRequest = {
      cadence: c.cadence,
      yearStartMonth: c.yearStartMonth,
      opensDaysBeforePeriodEnd: c.opensDaysBeforePeriodEnd,
      selfAssessmentDays: c.selfAssessmentDays,
      managerReviewDays: c.managerReviewDays,
      calibrationDays: c.calibrationDays,
      feedbackDays: c.feedbackDays,
    };
    this.draft.set(draft);
    this.baseline.set(JSON.stringify(draft));
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
