import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { normalizeHttpError } from 'app/core/api';
import { AlertComponent, BadgeComponent, ButtonComponent, EmptyStateComponent, SkeletonRowComponent } from 'app/shared/ui';

import { LeaveStatus } from '../models/enums/leave-status.enum';
import { LEAVE_TYPE_COLOR, LEAVE_TYPE_ICON } from '../models/enums/leave-type.enum';
import { LeaveRequest } from '../models/leave-request.model';
import { LeaveRequestService } from '../services/leave-request.service';

const DAY = 86_400_000;

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

interface Cell {
  date: string;
  type: string | null;
  pending: boolean;
  today: boolean;
  future: boolean;
  title: string;
}

/**
 * Time off for one employee.
 *
 * - Figures for this year: days taken, sick days, upcoming approved days and pending requests.
 * - A calendar heatmap from 12 months back to 3 months ahead, one cell per working day, coloured
 *   by leave type (pending requests hatched).
 * - Days per leave type over that window, and every request as a timeline card (dates, days,
 *   status, reason, approver and their comment).
 *
 * Loads `GET /leave-requests/employee/{id}/search` (newest first).
 */
@Component({
  selector: 'hum-employee-leave',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './employee-leave.component.html',
})
export class EmployeeLeaveComponent {
  readonly employeeId = input.required<string>();

  private readonly leaveService = inject(LeaveRequestService);

  protected readonly requests = signal<LeaveRequest[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly typeColor = (t: string): string => LEAVE_TYPE_COLOR[t] ?? 'var(--info-solid)';
  protected readonly typeIcon = (t: string): string => LEAVE_TYPE_ICON[t] ?? 'calendar';

  private readonly todayIso = iso(new Date());

  protected readonly stats = computed(() => {
    const list = this.requests() ?? [];
    const year = String(new Date().getFullYear());
    const approved = list.filter(r => r.status === LeaveStatus.APPROVED);
    const thisYear = approved.filter(r => r.startDate.startsWith(year) && r.startDate <= this.todayIso);
    const upcoming = approved.filter(r => r.startDate > this.todayIso).sort((a, b) => a.startDate.localeCompare(b.startDate));
    return {
      taken: thisYear.filter(r => r.leaveType !== 'SICK').reduce((s, r) => s + (r.daysCount ?? 0), 0),
      sick: thisYear.filter(r => r.leaveType === 'SICK').reduce((s, r) => s + (r.daysCount ?? 0), 0),
      upcomingDays: upcoming.reduce((s, r) => s + (r.daysCount ?? 0), 0),
      nextStart: upcoming[0]?.startDate ?? null,
      pending: list.filter(r => r.status === LeaveStatus.PENDING).length,
    };
  });

  /** Weeks (columns) × Mon–Fri (rows), from 12 months ago to 3 months ahead. */
  protected readonly calendar = computed(() => {
    const byDay = new Map<string, LeaveRequest>();
    for (const r of this.requests() ?? []) {
      if (r.status !== LeaveStatus.APPROVED && r.status !== LeaveStatus.PENDING) continue;
      for (let t = new Date(`${r.startDate}T00:00:00`).getTime(); t <= new Date(`${r.endDate}T00:00:00`).getTime(); t += DAY) {
        byDay.set(iso(new Date(t)), r);
      }
    }
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth() - 12, 1);
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7)); // back to Monday
    const end = new Date(now.getFullYear(), now.getMonth() + 4, 0);
    const weeks: { cells: Cell[]; month: string | null }[] = [];
    let lastMonth = -1;
    for (let w = new Date(start); w <= end; w = new Date(w.getFullYear(), w.getMonth(), w.getDate() + 7)) {
      const cells: Cell[] = [];
      for (let d = 0; d < 5; d++) {
        const day = new Date(w.getFullYear(), w.getMonth(), w.getDate() + d);
        const key = iso(day);
        const r = byDay.get(key);
        cells.push({
          date: key,
          type: r?.leaveType ?? null,
          pending: r?.status === LeaveStatus.PENDING,
          today: key === this.todayIso,
          future: key > this.todayIso,
          title: day.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }),
        });
      }
      // Label the week holding a month's first working days (so the first, partial week stays blank).
      const friday = new Date(w.getFullYear(), w.getMonth(), w.getDate() + 4);
      const month = friday.getMonth();
      const label = month !== lastMonth && friday.getDate() <= 7 ? friday.toLocaleDateString(undefined, { month: 'short' }) : null;
      weeks.push({ cells, month: label });
      if (label) lastMonth = month;
    }
    return weeks;
  });

  /** Days per type over the calendar window (approved only). */
  protected readonly byType = computed(() => {
    const since = iso(new Date(new Date().getFullYear(), new Date().getMonth() - 12, 1));
    const totals = new Map<string, number>();
    for (const r of this.requests() ?? []) {
      if (r.status !== LeaveStatus.APPROVED || r.startDate < since) continue;
      totals.set(r.leaveType, (totals.get(r.leaveType) ?? 0) + (r.daysCount ?? 0));
    }
    const max = Math.max(1, ...totals.values());
    return [...totals.entries()].sort((a, b) => b[1] - a[1]).map(([type, days]) => ({ type, days, pct: (days / max) * 100 }));
  });

  constructor() {
    effect(() => {
      const id = this.employeeId();
      untracked(() => this.load(id));
    });
  }

  protected load(id = this.employeeId()): void {
    this.error.set(null);
    this.leaveService.searchByEmployee(id, {}, { page: 0, size: 200, sort: ['startDate,desc'] }).subscribe({
      next: page => this.requests.set(page.content),
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.requests.set([]);
      },
    });
  }

  protected isUpcoming(r: LeaveRequest): boolean {
    return r.startDate > this.todayIso;
  }

  protected isOngoing(r: LeaveRequest): boolean {
    return r.startDate <= this.todayIso && r.endDate >= this.todayIso && r.status === LeaveStatus.APPROVED;
  }
}
