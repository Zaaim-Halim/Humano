import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  SkeletonComponent,
  SwitchComponent,
  ToastService,
} from 'app/shared/ui';

import { RetentionCycle, ReviewRetention, ReviewRetentionSettings } from './review-retention.model';
import { ReviewRetentionService } from './review-retention.service';

/** The server's limits on the retention period, in years. */
const MIN_YEARS = 1;
const MAX_YEARS = 30;

/**
 * Record retention — `/review-cycles/retention` (`CONFIGURE_PERFORMANCE_CYCLES`): how long review
 * records are kept, and deleting them after.
 *
 * - The period: whole years after a cycle's review period ends, and whether a nightly job deletes
 *   cycles that are due. Saving deletes nothing.
 * - Due: archived cycles past the period with nothing open — deleted whole, here (after typing the
 *   confirmation word) or by the job. The server deletes exactly the list shown, or refuses.
 * - Held: past the period but not archived, or with a dispute, improvement plan or compensation
 *   proposal still open; each says why.
 */
@Component({
  selector: 'hum-review-retention',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
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
    InputComponent,
    SkeletonComponent,
    SwitchComponent,
  ],
  templateUrl: './review-retention.component.html',
})
export default class ReviewRetentionComponent {
  private readonly retentionService = inject(ReviewRetentionService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly minYears = MIN_YEARS;
  protected readonly maxYears = MAX_YEARS;

  protected readonly loaded = signal<ReviewRetention | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly enabled = signal(false);
  /** The years field as typed. */
  protected readonly years = signal('');

  protected readonly confirming = signal(false);
  protected readonly confirmText = signal('');
  protected readonly purging = signal(false);

  protected readonly yearsValue = computed(() => Number(this.years()));
  protected readonly yearsInvalid = computed(() => {
    const y = this.yearsValue();
    return this.years().trim() === '' || !Number.isInteger(y) || y < MIN_YEARS || y > MAX_YEARS;
  });
  protected readonly dirty = computed(() => {
    const l = this.loaded();
    return !!l && (l.enabled !== this.enabled() || String(l.retainYears) !== this.years().trim());
  });
  protected readonly dueReviews = computed(() => (this.loaded()?.due ?? []).reduce((sum, c) => sum + c.reviews, 0));
  protected readonly confirmWord = computed(() => this.label('humano.performance.retention.confirm.word'));
  protected readonly confirmed = computed(() => this.confirmText().trim().toUpperCase() === this.confirmWord().toUpperCase());

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.retentionService.get().subscribe({
      next: r => this.apply(r),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected discard(): void {
    const l = this.loaded();
    if (l) this.apply(l);
  }

  protected save(): void {
    if (!this.dirty() || this.yearsInvalid()) return;
    const body: ReviewRetentionSettings = { enabled: this.enabled(), retainYears: this.yearsValue() };
    this.saving.set(true);
    this.retentionService.save(body).subscribe({
      next: r => {
        this.apply(r);
        this.saving.set(false);
        this.toast.success(this.label('humano.performance.retention.saved'));
      },
      error: (err: unknown) => {
        this.toast.danger(normalizeHttpError(err));
        this.saving.set(false);
      },
    });
  }

  protected openConfirm(): void {
    this.confirmText.set('');
    this.confirming.set(true);
  }

  protected purge(): void {
    const due = this.loaded()?.due ?? [];
    if (!due.length || !this.confirmed()) return;
    this.purging.set(true);
    this.retentionService.purge(due.map(c => c.id)).subscribe({
      next: result => {
        this.purging.set(false);
        this.confirming.set(false);
        this.toast.success(this.label('humano.performance.retention.purged', { cycles: result.cycles, reviews: result.reviews }));
        if (result.failed) {
          this.toast.warning(this.label('humano.performance.retention.purgeFailed', { count: result.failed }));
        }
        this.load();
      },
      error: (err: unknown) => {
        // Usually the list changed meanwhile: show the current one.
        this.purging.set(false);
        this.confirming.set(false);
        this.toast.danger(normalizeHttpError(err));
        this.load();
      },
    });
  }

  /** Why a cycle is held back, as short labels. */
  protected reasons(c: RetentionCycle): string[] {
    const h = c.holds;
    const out: string[] = [];
    if (h.notArchived) out.push(this.label('humano.performance.retention.hold.notArchived'));
    if (h.openDisputes) out.push(this.label('humano.performance.retention.hold.disputes', { count: h.openDisputes }));
    if (h.openPlans) out.push(this.label('humano.performance.retention.hold.plans', { count: h.openPlans }));
    if (h.pendingProposals) out.push(this.label('humano.performance.retention.hold.proposals', { count: h.pendingProposals }));
    return out;
  }

  private apply(r: ReviewRetention): void {
    this.loaded.set(r);
    this.enabled.set(r.enabled);
    this.years.set(String(r.retainYears));
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
