import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { BadgeTone } from 'app/config/status-map';
import { normalizeHttpError } from 'app/core/api';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  SkeletonComponent,
  SwitchComponent,
} from 'app/shared/ui';

import { ImprovementPlan, ImprovementPlanOutcome, ImprovementPlanStatus } from '../models/improvement-plan.model';
import { ImprovementPlanService } from '../services/improvement-plan.service';

const DAY = 24 * 60 * 60 * 1000;
const STATUS_TONE: Record<ImprovementPlanStatus, BadgeTone> = { DRAFT: 'neutral', ACTIVE: 'warning', CLOSED: 'info' };
const OUTCOME_TONE: Record<ImprovementPlanOutcome, BadgeTone> = { SUCCESSFUL: 'success', UNSUCCESSFUL: 'danger', CANCELLED: 'neutral' };

/**
 * Improvement plans — `/improvement-plans`: the plans the signed-in user runs (their direct reports'
 * and the ones they started; every plan for HR with `MANAGE_PERFORMANCE_REVIEWS`), open first, and
 * their own plans once shared with them. Plans start from a review page, never from here.
 */
@Component({
  selector: 'hum-improvement-plans',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SwitchComponent,
  ],
  templateUrl: './improvement-plans.component.html',
})
export default class ImprovementPlansComponent {
  private readonly planService = inject(ImprovementPlanService);

  protected readonly managed = signal<ImprovementPlan[] | null>(null);
  protected readonly mine = signal<ImprovementPlan[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly showClosed = signal(false);

  protected readonly visible = computed(() =>
    (this.managed() ?? [])
      .filter(p => this.showClosed() || p.status !== 'CLOSED')
      .sort((a, b) => order(a) - order(b) || a.endDate.localeCompare(b.endDate)),
  );
  protected readonly closedCount = computed(() => (this.managed() ?? []).filter(p => p.status === 'CLOSED').length);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    forkJoin([this.planService.managed(), this.planService.mine()]).subscribe({
      next: ([managed, mine]) => {
        this.managed.set(managed);
        this.mine.set(mine);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected statusTone(s: ImprovementPlanStatus): BadgeTone {
    return STATUS_TONE[s];
  }

  protected outcomeTone(o: ImprovementPlanOutcome): BadgeTone {
    return OUTCOME_TONE[o];
  }

  protected daysLeft(p: ImprovementPlan): number | null {
    return p.status === 'ACTIVE' ? Math.ceil((Date.parse(p.endDate) - Date.parse(new Date().toISOString().slice(0, 10))) / DAY) : null;
  }
}

/** Running plans first, then drafts, then closed ones. */
function order(p: ImprovementPlan): number {
  return p.status === 'ACTIVE' ? 0 : p.status === 'DRAFT' ? 1 : 2;
}
