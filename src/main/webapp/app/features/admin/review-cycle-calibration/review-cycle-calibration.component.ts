import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import {
  CurrentEmployeeService,
  PerformanceReview,
  RATING_SCALE,
  REVIEW_POTENTIALS,
  ReviewCycle,
  ReviewCycleService,
  ReviewPotential,
} from 'app/features/employee';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  EmptyStateComponent,
  FormFieldComponent,
  SelectComponent,
  SelectOption,
  SkeletonComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

const PAGE = 1000;
const ALL = '';

/** Performance band of a final rating: 1–2 below, 3 meets, 4–5 above expectations. */
type Band = 'LOW' | 'MID' | 'HIGH';
const BANDS: Band[] = ['LOW', 'MID', 'HIGH'];
/** Top row first: the grid reads high potential at the top, as is customary. */
const POTENTIAL_ROWS: ReviewPotential[] = ['HIGH', 'MODERATE', 'LOW'];
type BoxKey = `${ReviewPotential}_${Band}`;

/**
 * A reference distribution of final ratings (percent per rating), shown next to the actual one.
 * Guidance only: ratings are never forced to fit it.
 */
const GUIDANCE: Record<number, number> = { 1: 5, 2: 15, 3: 50, 4: 20, 5: 10 };

/**
 * Calibration session for a cycle — `/review-cycles/:id/calibration` — where HR and management
 * compare the written reviews before they are shared:
 *
 * - the distribution of final ratings against the reviewers' own and a reference guidance;
 * - the 9-box — performance (final rating) against potential — with who sits in each box;
 *   selecting a box filters the list;
 * - the list of written reviews, by department and reviewer, with the reviewer's and final rating,
 *   potential and promotion recommendation.
 *
 * During the calibration phase, `MANAGE_CALIBRATION` adjusts a review's rating and potential here
 * (a changed rating needs a reason, and every change is logged on the review). Reviews waiting for
 * their sign-off, or returned, can't be calibrated yet; nobody calibrates their own review.
 */
@Component({
  selector: 'hum-review-cycle-calibration',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    EmptyStateComponent,
    FormFieldComponent,
    SelectComponent,
    SkeletonComponent,
    SkeletonRowComponent,
    TextareaComponent,
  ],
  templateUrl: './review-cycle-calibration.component.html',
})
export default class ReviewCycleCalibrationComponent {
  /** Route param. */
  readonly id = input.required<string>();

  private readonly reviewCycleService = inject(ReviewCycleService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService);

  protected readonly canCalibrate = this.account.hasPermission(Permission.MANAGE_CALIBRATION);
  protected readonly scale = RATING_SCALE;
  protected readonly bands = BANDS;
  protected readonly rows = POTENTIAL_ROWS;

  protected readonly cycle = signal<ReviewCycle | null>(null);
  protected readonly reviews = signal<PerformanceReview[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly department = signal(ALL);
  protected readonly reviewer = signal(ALL);
  protected readonly box = signal<BoxKey | null>(null);

  /** Ratings change only while the cycle is in calibration. */
  protected readonly open = computed(() => this.canCalibrate && this.cycle()?.phase === 'CALIBRATION');

  /** Written reviews (the reviewer has rated them) still in the cycle, in the chosen department and reviewer. */
  protected readonly written = computed(() =>
    (this.reviews() ?? [])
      .filter(r => r.managerAssessment !== null && r.rating !== null && r.status !== 'EXCLUDED')
      .filter(r => this.department() === ALL || (r.departmentName ?? '') === this.department())
      .filter(r => this.reviewer() === ALL || (r.reviewerName ?? '') === this.reviewer()),
  );
  /** Waiting for their sign-off or returned at it: not calibratable yet. */
  protected readonly blocked = computed(() => this.written().filter(r => !signedOff(r)).length);

  protected readonly departmentOptions = computed(() => this.options('department', r => r.departmentName));
  protected readonly reviewerOptions = computed(() => this.options('reviewer', r => r.reviewerName));

  protected readonly distribution = computed(() => {
    const list = this.written();
    const total = list.length;
    const share = (count: number): number => (total ? Math.round((count / total) * 100) : 0);
    return {
      total,
      bars: RATING_SCALE.map(n => {
        const final = list.filter(r => r.rating === n).length;
        const given = list.filter(r => r.managerAssessment?.rating === n).length;
        return { rating: n, final, finalShare: share(final), givenShare: share(given), guidance: GUIDANCE[n] };
      }),
    };
  });

  /** The 9-box: who sits where, and who has no potential yet. */
  protected readonly grid = computed(() => {
    const cells = new Map<BoxKey, PerformanceReview[]>();
    const unplaced: PerformanceReview[] = [];
    for (const r of this.written()) {
      const potential = r.managerAssessment?.potential;
      if (!potential) {
        unplaced.push(r);
        continue;
      }
      const key = boxOf(potential, r.rating!);
      cells.set(key, [...(cells.get(key) ?? []), r]);
    }
    return { cells, unplaced };
  });

  protected readonly visible = computed(() => {
    const box = this.box();
    return this.written()
      .filter(r => !box || (r.managerAssessment?.potential && boxOf(r.managerAssessment.potential, r.rating!) === box))
      .sort((a, b) => (a.employeeName ?? '').localeCompare(b.employeeName ?? ''));
  });

  // Calibration dialog.
  protected readonly editing = signal<PerformanceReview | null>(null);
  protected readonly rating = signal<number | null>(null);
  protected readonly potential = signal<ReviewPotential | ''>('');
  protected readonly note = signal('');
  protected readonly ratingOptions: SelectOption[] = RATING_SCALE.map(r => ({
    value: String(r),
    label: `${r} — ${this.label(`humano.performance.rating.${r}`)}`,
  }));
  protected readonly potentialOptions: SelectOption[] = REVIEW_POTENTIALS.map(p => ({
    value: p,
    label: this.label(`humano.performance.potential.${p}`),
  }));
  protected readonly noteMissing = computed(() => {
    const own = this.editing()?.managerAssessment?.rating;
    return this.rating() !== null && this.rating() !== own && !this.note().trim();
  });

  constructor() {
    this.currentEmployee.resolve();
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(id => this.load(id));
  }

  protected load(id = this.id()): void {
    this.error.set(null);
    this.reviewCycleService.find(id).subscribe({
      next: c => {
        this.cycle.set(c);
        this.reviews.set(null);
        this.reviewCycleService.reviews(c.cycleId, undefined, { page: 0, size: PAGE }).subscribe({
          next: page => this.reviews.set(page.content),
          error: (err: unknown) => this.error.set(normalizeHttpError(err)),
        });
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected people(band: Band, potential: ReviewPotential): PerformanceReview[] {
    return this.grid().cells.get(`${potential}_${band}`) ?? [];
  }

  protected toggleBox(band: Band, potential: ReviewPotential): void {
    const key: BoxKey = `${potential}_${band}`;
    this.box.set(this.box() === key ? null : key);
  }

  protected isBox(band: Band, potential: ReviewPotential): boolean {
    return this.box() === `${potential}_${band}`;
  }

  protected boxLabel(key: BoxKey): string {
    const [potential, band] = key.split('_') as [ReviewPotential, Band];
    return this.label(`humano.performance.calibration.box.${potential}_${band}`);
  }

  protected canCalibrateRow(r: PerformanceReview): boolean {
    return this.open() && signedOff(r) && r.employeeId !== this.currentEmployee.currentEmployeeId();
  }

  protected isCalibrated(r: PerformanceReview): boolean {
    return r.managerAssessment?.rating != null && r.rating !== r.managerAssessment.rating;
  }

  protected edit(r: PerformanceReview): void {
    this.rating.set(r.rating);
    this.potential.set(r.managerAssessment?.potential ?? '');
    this.note.set(r.managerAssessment?.calibrationNote ?? '');
    this.editing.set(r);
  }

  protected save(): void {
    const r = this.editing();
    const rating = this.rating();
    if (!r?.cycleId || rating === null || this.noteMissing()) return;
    const note = this.note().trim();
    const potential = this.potential();
    this.saving.set(true);
    this.reviewCycleService
      .calibrate(r.cycleId, r.id, { rating, ...(note ? { note } : {}), ...(potential ? { potential } : {}) })
      .subscribe({
        next: updated => {
          this.reviews.update(list => (list ?? []).map(x => (x.id === updated.id ? updated : x)));
          this.saving.set(false);
          this.editing.set(null);
          this.toast.success(this.label('humano.performance.review.calibrated', { name: updated.employeeName }));
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  private options(kind: 'department' | 'reviewer', value: (r: PerformanceReview) => string | null): SelectOption[] {
    const names = [...new Set((this.reviews() ?? []).map(value).filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b));
    return [{ value: ALL, label: this.label(`humano.performance.calibration.all.${kind}`) }, ...names.map(n => ({ value: n, label: n }))];
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}

function bandOf(rating: number): Band {
  return rating <= 2 ? 'LOW' : rating === 3 ? 'MID' : 'HIGH';
}

function boxOf(potential: ReviewPotential, rating: number): BoxKey {
  return `${potential}_${bandOf(rating)}`;
}

/** No sign-off needed, or signed off or waived. */
function signedOff(r: PerformanceReview): boolean {
  return !r.signOff || r.signOff.status === 'APPROVED' || r.signOff.status === 'WAIVED';
}
