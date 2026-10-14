import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { AlertComponent, BadgeComponent, ButtonComponent, CheckboxComponent, SkeletonRowComponent, ToastService } from 'app/shared/ui';

import { ClockEvent, ClockEventType, TimeClockDay } from '../models/time-clock.model';
import { MeWorkService } from '../services/me-work.service';

const WEEKLY_TARGET_MIN = 40 * 60;

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** "HH:mm[:ss]" → minutes after midnight. */
function minutes(time: string | null): number | null {
  if (!time) return null;
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

interface Segment {
  kind: 'work' | 'break';
  from: number;
  to: number;
  open: boolean;
}

/**
 * The employee's time clock (`/api/me/attendance`).
 *
 * - Today: a live clock card — clock in (optionally working from home), start a lunch / short /
 *   meeting break, end it, clock out — with worked and break time ticking while the day is open,
 *   and a timeline of today's work and breaks plus every clock event.
 * - The selected week: one row per day with check-in → check-out, work/break bars on a shared
 *   time scale, and worked time against a 40 h week.
 *
 * The server stamps every time in the company timezone and enforces the order of actions;
 * `clocked` reports worked hours per day so the project-hours form can reuse them.
 */
@Component({
  selector: 'hum-time-clock',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    CheckboxComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './time-clock.component.html',
})
export class TimeClockComponent {
  /** Monday of the week to show. */
  readonly week = input.required<Date>();
  /** Clocked hours per ISO date for the shown week. */
  readonly clocked = output<Record<string, number>>();

  private readonly meWork = inject(MeWorkService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  protected readonly today = signal<TimeClockDay | null>(null);
  protected readonly todayError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly remote = signal(false);
  protected readonly days = signal<TimeClockDay[] | null>(null);

  /** Ticks every 20 s so open days count up without refetching. */
  private readonly tick = signal(Date.now());
  private fetchedAt = Date.now();

  protected readonly breakTypes: { type: ClockEventType; icon: string; key: string }[] = [
    { type: 'LUNCH_BREAK', icon: 'utensils', key: 'humano.timeclock.lunch' },
    { type: 'OTHER', icon: 'coffee', key: 'humano.timeclock.shortBreak' },
    { type: 'MEETING', icon: 'users', key: 'humano.timeclock.meetingBreak' },
  ];

  /** Minutes elapsed since the last fetch (for live totals). */
  private readonly elapsed = computed(() => Math.max(0, Math.floor((this.tick() - this.fetchedAt) / 60000)));
  protected readonly liveWorked = computed(() => {
    const d = this.today();
    return d ? d.workedMinutes + (d.state === 'WORKING' ? this.elapsed() : 0) : 0;
  });
  protected readonly liveBreak = computed(() => {
    const d = this.today();
    return d ? d.breakMinutes + (d.state === 'ON_BREAK' ? this.elapsed() : 0) : 0;
  });
  protected readonly openBreak = computed(() => {
    const d = this.today();
    if (d?.state !== 'ON_BREAK') return null;
    return [...d.events].reverse().find(e => e.action === 'BREAK_START') ?? null;
  });
  protected readonly nowLabel = computed(() => new Date(this.tick()).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }));

  /** Shared time scale for the timelines: 07:00–19:00, widened to fit the data. */
  protected readonly scale = computed(() => {
    let lo = 7 * 60;
    let hi = 19 * 60;
    for (const d of [...(this.days() ?? []), ...(this.today() ? [this.today()!] : [])]) {
      const a = minutes(d.checkIn);
      const b = minutes(d.checkOut) ?? (d.state === 'NOT_STARTED' ? null : this.nowMinutes());
      if (a !== null) lo = Math.min(lo, Math.floor(a / 60) * 60);
      if (b !== null) hi = Math.max(hi, Math.ceil(b / 60) * 60);
    }
    return { lo, hi, hours: Array.from({ length: (hi - lo) / 60 + 1 }, (_, i) => lo / 60 + i) };
  });

  protected readonly todaySegments = computed(() => (this.today() ? this.segments(this.today()!, true) : []));

  protected readonly weekRows = computed(() => {
    const byDate = new Map((this.days() ?? []).map(d => [d.date, d]));
    const todayIso = iso(new Date());
    return Array.from({ length: 7 }, (_, i) => {
      const date = new Date(this.week().getFullYear(), this.week().getMonth(), this.week().getDate() + i);
      const key = iso(date);
      const day = key === todayIso && this.today()?.attendanceId ? this.today()! : (byDate.get(key) ?? null);
      return {
        iso: key,
        date,
        weekend: i >= 5,
        isToday: key === todayIso,
        day,
        worked: key === todayIso && day ? this.liveWorked() : (day?.workedMinutes ?? 0),
        segments: day ? this.segments(day, key === todayIso) : [],
      };
    });
  });
  protected readonly weekTotal = computed(() => this.weekRows().reduce((s, r) => s + r.worked, 0));
  protected readonly weekBreaks = computed(() => (this.days() ?? []).reduce((s, d) => s + d.breakMinutes, 0));
  protected readonly weekTarget = WEEKLY_TARGET_MIN;

  constructor() {
    this.loadToday();
    const timer = setInterval(() => this.tick.set(Date.now()), 20_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    effect(() => {
      const monday = this.week();
      untracked(() => this.loadWeek(monday));
    });
  }

  protected clockIn(): void {
    this.act(this.meWork.clockIn({ remote: this.remote() }), 'humano.timeclock.clockedIn');
  }

  protected startBreak(type: ClockEventType): void {
    this.act(this.meWork.startBreak({ breakType: type }), 'humano.timeclock.breakStarted');
  }

  protected endBreak(): void {
    this.act(this.meWork.endBreak(), 'humano.timeclock.breakEnded');
  }

  protected clockOut(): void {
    this.act(this.meWork.clockOut(), 'humano.timeclock.clockedOut');
  }

  protected loadToday(): void {
    this.todayError.set(null);
    this.meWork.clockToday().subscribe({
      next: d => this.setToday(d),
      error: (err: unknown) => this.todayError.set(normalizeHttpError(err)),
    });
  }

  /** "7 h 45 min". */
  protected duration(total: number): string {
    const h = Math.floor(total / 60);
    const m = total % 60;
    return h ? `${h} h ${String(m).padStart(2, '0')} min` : `${m} min`;
  }

  /** "08:42" from "08:42:00". */
  protected hm(time: string | null): string {
    return time ? time.slice(0, 5) : '—';
  }

  /** Position on the shared scale, in %. */
  protected pos(min: number): number {
    const s = this.scale();
    return ((min - s.lo) / (s.hi - s.lo)) * 100;
  }

  protected eventIcon(e: ClockEvent): string {
    switch (e.action) {
      case 'CHECK_IN':
        return 'log-in';
      case 'CHECK_OUT':
        return 'log-out';
      case 'BREAK_START':
        return e.type === 'LUNCH_BREAK' ? 'utensils' : e.type === 'MEETING' ? 'users' : 'coffee';
      default:
        return 'play';
    }
  }

  protected eventLabel(e: ClockEvent): string {
    const kind = e.type === 'LUNCH_BREAK' ? 'lunch' : e.type === 'MEETING' ? 'meetingBreak' : 'shortBreak';
    switch (e.action) {
      case 'CHECK_IN':
        return this.t('humano.timeclock.event.in');
      case 'CHECK_OUT':
        return this.t('humano.timeclock.event.out');
      case 'BREAK_START':
        return this.t('humano.timeclock.event.breakStart', { kind: this.t(`humano.timeclock.${kind}`) });
      case 'BREAK_END':
        return this.t('humano.timeclock.event.breakEnd', { kind: this.t(`humano.timeclock.${kind}`) });
      default:
        return e.action;
    }
  }

  private act(request$: Observable<TimeClockDay>, messageKey: string): void {
    this.busy.set(true);
    request$.subscribe({
      next: d => {
        this.busy.set(false);
        this.setToday(d);
        this.toast.success(this.t(messageKey, { time: this.hm(this.lastEventTime(d)) }));
        this.loadWeek(this.week());
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
        this.loadToday(); // the state may have changed elsewhere (another tab, HR)
      },
    });
  }

  private setToday(d: TimeClockDay): void {
    this.fetchedAt = Date.now();
    this.tick.set(this.fetchedAt);
    this.today.set(d);
  }

  private loadWeek(monday: Date): void {
    const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
    this.meWork.clockDays(iso(monday), iso(sunday)).subscribe({
      next: days => {
        this.days.set(days);
        this.clocked.emit(Object.fromEntries(days.map(d => [d.date, Math.round((d.workedMinutes / 60) * 4) / 4])));
      },
      error: () => this.days.set([]),
    });
  }

  private lastEventTime(d: TimeClockDay): string | null {
    return d.events.length ? d.events[d.events.length - 1].time : null;
  }

  private nowMinutes(): number {
    const now = new Date(this.tick());
    return now.getHours() * 60 + now.getMinutes();
  }

  /** Work and break spans of a day (an open day runs to now when it is today). */
  private segments(d: TimeClockDay, live: boolean): Segment[] {
    const start = minutes(d.checkIn);
    if (start === null) return [];
    const end = minutes(d.checkOut) ?? (live ? this.nowMinutes() : start);
    const out: Segment[] = [];
    let cursor = start;
    let breakFrom: number | null = null;
    for (const e of d.events) {
      const t = minutes(e.time)!;
      if (e.action === 'BREAK_START') {
        if (t > cursor) out.push({ kind: 'work', from: cursor, to: t, open: false });
        breakFrom = t;
      } else if (e.action === 'BREAK_END' && breakFrom !== null) {
        out.push({ kind: 'break', from: breakFrom, to: t, open: false });
        cursor = t;
        breakFrom = null;
      }
    }
    const open = !d.checkOut && live;
    if (breakFrom !== null) out.push({ kind: 'break', from: breakFrom, to: Math.max(breakFrom, end), open });
    else if (end > cursor) out.push({ kind: 'work', from: cursor, to: end, open });
    return out;
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return String(this.translate.instant(key, params));
  }
}
