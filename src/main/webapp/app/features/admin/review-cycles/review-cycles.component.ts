import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { normalizeHttpError } from 'app/core/api';
import { ReviewCalendar, ReviewCalendarService, ReviewCycle, ReviewCycleService } from 'app/features/employee';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  ProgressComponent,
  SkeletonComponent,
  TabItem,
  TabsComponent,
} from 'app/shared/ui';

import { daysBetween, localIsoDate } from '../models/onboarding.model';
import { phaseDeadline } from './review-cycle.util';
import { ReviewCycleFormComponent } from './review-cycle-form.component';

type View = 'current' | 'closed';
const CLOSED = ['COMPLETED', 'ARCHIVED'];

/**
 * Review cycles — `/review-cycles` (`VIEW_PERFORMANCE_REVIEWS`), in two tabs (`?view=`):
 *
 * - Current: drafts and running cycles — phase, progress on the current phase's step, people in
 *   scope and the phase deadline with a countdown — with an overview on top.
 * - Closed: completed and archived cycles.
 *
 * With `CONFIGURE_PERFORMANCE_CYCLES`, "New cycle" opens the form pre-filled from the review
 * calendar's next proposed cycle (also offered in a banner while it isn't set up), and "Review
 * calendar" opens the calendar settings. A row opens the cycle.
 */
@Component({
  selector: 'hum-review-cycles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    ProgressComponent,
    SkeletonComponent,
    TabsComponent,
    ReviewCycleFormComponent,
  ],
  templateUrl: './review-cycles.component.html',
})
export default class ReviewCyclesComponent {
  private readonly reviewCycleService = inject(ReviewCycleService);
  private readonly calendarService = inject(ReviewCalendarService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

  protected readonly canConfigure = inject(AccountService).hasPermission(Permission.CONFIGURE_PERFORMANCE_CYCLES);
  protected readonly today = localIsoDate();

  protected readonly view = signal<View>(this.route.snapshot.queryParamMap.get('view') === 'closed' ? 'closed' : 'current');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'current', label: this.label('humano.performance.cycles.tabCurrent'), icon: 'refresh-ccw' },
    { id: 'closed', label: this.label('humano.performance.cycles.tabClosed'), icon: 'archive' },
  ]);
  protected readonly cycles = signal<ReviewCycle[] | null>(null);
  protected readonly calendar = signal<ReviewCalendar | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly formOpen = signal(false);

  protected readonly rows = computed(() => {
    const all = this.cycles() ?? [];
    return this.view() === 'closed' ? all.filter(c => CLOSED.includes(c.phase)) : all.filter(c => !CLOSED.includes(c.phase));
  });
  protected readonly stats = computed(() => {
    const running = (this.cycles() ?? []).filter(c => !CLOSED.includes(c.phase) && c.phase !== 'DRAFT');
    const deadlines = running
      .map(c => ({ cycle: c, date: phaseDeadline(c) }))
      .filter((d): d is { cycle: ReviewCycle; date: string } => !!d.date)
      .sort((a, b) => a.date.localeCompare(b.date));
    return {
      running: running.length,
      drafts: (this.cycles() ?? []).filter(c => c.phase === 'DRAFT').length,
      people: running.reduce((sum, c) => sum + c.progress.totalEmployees, 0),
      shared: running.reduce((sum, c) => sum + c.progress.deliveredFeedbacks, 0),
      next: deadlines[0] ?? null,
    };
  });
  /** The calendar's next cycle, offered while no cycle has its name. */
  protected readonly proposal = computed(() => {
    const next = this.calendar()?.nextCycle;
    if (!next || !this.canConfigure) return null;
    return (this.cycles() ?? []).some(c => c.name === next.name) ? null : next;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.cycles.set(null);
    this.reviewCycleService.list({ page: 0, size: 200, sort: ['startDate,desc'] }).subscribe({
      next: page => this.cycles.set(page.content),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.cycles.set([]);
      },
    });
    this.calendarService.get().subscribe({ next: c => this.calendar.set(c), error: () => this.calendar.set(null) });
  }

  protected setView(view: string | undefined): void {
    const next: View = view === 'closed' ? 'closed' : 'current';
    if (next === this.view()) return;
    this.view.set(next);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { view: next === 'closed' ? 'closed' : null }, replaceUrl: true });
  }

  protected deadline(c: ReviewCycle): string | null {
    return phaseDeadline(c);
  }

  /** "Due in 5 d", "Due today", "3 d overdue". */
  protected countdown(date: string): string {
    const days = daysBetween(this.today, date);
    if (days > 0) return this.label('humano.performance.cycles.dueIn', { count: days });
    if (days === 0) return this.label('humano.performance.cycles.dueToday');
    return this.label('humano.performance.cycles.overdue', { count: -days });
  }

  protected created(cycle: ReviewCycle): void {
    this.formOpen.set(false);
    void this.router.navigate(['/review-cycles', cycle.cycleId]);
  }

  private label(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
