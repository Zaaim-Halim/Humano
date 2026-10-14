import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, output, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { BadgeTone } from 'app/config/status-map';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  EmptyStateComponent,
  IconButtonComponent,
  InputComponent,
  SkeletonRowComponent,
} from 'app/shared/ui';

import { TeamAttendance, TeamAttendanceRow, TeamPresence } from '../models/time-clock.model';
import { MeWorkService } from '../services/me-work.service';
import { clockDuration, clockScale, clockSegments, hm } from './clock-timeline';

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Badge tone and icon per presence. */
const PRESENCE: Record<TeamPresence, { tone: BadgeTone; icon: string }> = {
  WORKING: { tone: 'success', icon: 'timer' },
  ON_BREAK: { tone: 'warning', icon: 'coffee' },
  DONE: { tone: 'brand', icon: 'circle-check' },
  MISSING_CLOCK_OUT: { tone: 'danger', icon: 'triangle-alert' },
  ON_LEAVE: { tone: 'info', icon: 'palmtree' },
  NOT_IN: { tone: 'neutral', icon: 'clock' },
  ABSENT: { tone: 'danger', icon: 'user-x' },
  OFF: { tone: 'neutral', icon: 'moon' },
};

/**
 * Team attendance — one day of clocking for the people the viewer is responsible for
 * (`GET /api/me/attendance/team`): a manager's whole reporting line, or everyone for HR.
 *
 * Counts per presence (working, on a break, done, missing clock-out, on leave, not in / absent)
 * double as filters; HR can also narrow to a team. Each person shows their check-in → check-out,
 * worked and break time, and a work/break bar on a shared hour scale; today refreshes every minute.
 * Emits `scopeChange` so the page can hide the tab from people with no one to see.
 */
@Component({
  selector: 'hum-team-attendance',
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
    IconButtonComponent,
    InputComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './team-attendance.component.html',
})
export class TeamAttendanceComponent {
  readonly scopeChange = output<TeamAttendance['scope']>();

  private readonly meWork = inject(MeWorkService);
  private readonly translate = inject(TranslateService);
  private readonly canOpenProfiles = inject(AccountService).hasPermission(Permission.READ_EMPLOYEE);

  protected readonly date = signal(iso(new Date()));
  protected readonly data = signal<TeamAttendance | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly presenceFilter = signal<TeamPresence | ''>('');
  protected readonly unitFilter = signal('');
  protected readonly query = signal('');
  private readonly tick = signal(Date.now());

  protected readonly isToday = computed(() => this.date() === iso(new Date()));
  protected readonly presenceStyle = (p: TeamPresence) => PRESENCE[p];
  protected readonly duration = clockDuration;
  protected readonly hm = hm;

  /** Presence chips with counts (only the ones that occur), in display order. */
  protected readonly chips = computed(() => {
    const counts = new Map<TeamPresence, number>();
    for (const r of this.unitRows()) counts.set(r.presence, (counts.get(r.presence) ?? 0) + 1);
    return (Object.keys(PRESENCE) as TeamPresence[]).filter(p => counts.has(p)).map(p => ({ presence: p, count: counts.get(p)! }));
  });
  protected readonly units = computed(() =>
    [...new Set((this.data()?.rows ?? []).map(r => r.unitName).filter((u): u is string => !!u))].sort(),
  );
  private readonly unitRows = computed(() => {
    const unit = this.unitFilter();
    return (this.data()?.rows ?? []).filter(r => !unit || r.unitName === unit);
  });
  protected readonly rows = computed(() => {
    const p = this.presenceFilter();
    const q = this.query().trim().toLowerCase();
    return this.unitRows().filter(
      r => (!p || r.presence === p) && (!q || r.name.toLowerCase().includes(q) || (r.jobTitle ?? '').toLowerCase().includes(q)),
    );
  });
  protected readonly scale = computed(() =>
    clockScale(
      (this.data()?.rows ?? []).flatMap(r => (r.day ? [r.day] : [])),
      this.nowMinutes(),
    ),
  );
  protected readonly stats = computed(() => {
    const rows = this.unitRows();
    const worked = rows.reduce((s, r) => s + (r.day?.workedMinutes ?? 0), 0);
    const clocked = rows.filter(r => r.day?.checkIn).length;
    return { people: rows.length, clocked, averageWorked: clocked ? Math.round(worked / clocked) : 0 };
  });

  constructor() {
    effect(() => {
      const d = this.date();
      untracked(() => this.load(d));
    });
    // Keep "today" live: re-fetch every minute while looking at today.
    const timer = setInterval(() => {
      this.tick.set(Date.now());
      if (this.isToday()) this.load(this.date(), true);
    }, 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected load(date = this.date(), quiet = false): void {
    if (!quiet) this.data.set(null);
    this.error.set(null);
    this.meWork.teamAttendance(date).subscribe({
      next: d => {
        this.data.set(d);
        this.scopeChange.emit(d.scope);
      },
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected shiftDay(delta: number): void {
    const d = new Date(`${this.date()}T00:00:00`);
    d.setDate(d.getDate() + delta);
    if (iso(d) <= iso(new Date())) this.date.set(iso(d));
  }

  protected goToday(): void {
    this.date.set(iso(new Date()));
  }

  protected togglePresence(p: TeamPresence): void {
    this.presenceFilter.set(this.presenceFilter() === p ? '' : p);
  }

  protected segments(r: TeamAttendanceRow): ReturnType<typeof clockSegments> {
    return r.day ? clockSegments(r.day, this.isToday(), this.nowMinutes()) : [];
  }

  protected pos(min: number): number {
    const s = this.scale();
    return ((min - s.lo) / (s.hi - s.lo)) * 100;
  }

  protected personLink(id: string): string[] {
    return this.canOpenProfiles ? ['/employees', id] : ['/people-tree', id];
  }

  protected presenceLabel(r: TeamAttendanceRow): string {
    if (r.presence === 'ON_LEAVE' && r.leaveType) return this.t(`humano.leave.type.${r.leaveType}`);
    return this.t(`humano.teamAttendance.presence.${r.presence}`);
  }

  private nowMinutes(): number {
    const now = new Date(this.tick());
    return now.getHours() * 60 + now.getMinutes();
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
