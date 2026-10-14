import { TimeClockDay } from '../models/time-clock.model';

/** A stretch of work or break, in minutes after midnight. */
export interface ClockSegment {
  kind: 'work' | 'break';
  from: number;
  to: number;
  /** Still running (today, not clocked out). */
  open: boolean;
}

/** "HH:mm[:ss]" → minutes after midnight. */
export function clockMinutes(time: string | null): number | null {
  if (!time) return null;
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

/** "08:42" from "08:42:00". */
export function hm(time: string | null): string {
  return time ? time.slice(0, 5) : '—';
}

/** "7 h 45 min". */
export function clockDuration(total: number): string {
  const h = Math.floor(total / 60);
  const m = total % 60;
  return h ? `${h} h ${String(m).padStart(2, '0')} min` : `${m} min`;
}

/** Work and break spans of a day; an open day runs to `nowMinutes` when `live`. */
export function clockSegments(d: TimeClockDay, live: boolean, nowMinutes: number): ClockSegment[] {
  const start = clockMinutes(d.checkIn);
  if (start === null) return [];
  const end = clockMinutes(d.checkOut) ?? (live ? nowMinutes : start);
  const out: ClockSegment[] = [];
  let cursor = start;
  let breakFrom: number | null = null;
  for (const e of d.events) {
    const t = clockMinutes(e.time)!;
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

/** An hour scale (07:00–19:00 at least) wide enough for every given day. */
export function clockScale(days: TimeClockDay[], nowMinutes: number): { lo: number; hi: number; hours: number[] } {
  let lo = 7 * 60;
  let hi = 19 * 60;
  for (const d of days) {
    const a = clockMinutes(d.checkIn);
    const b = clockMinutes(d.checkOut) ?? (d.state === 'NOT_STARTED' ? null : nowMinutes);
    if (a !== null) lo = Math.min(lo, Math.floor(a / 60) * 60);
    if (b !== null) hi = Math.max(hi, Math.ceil(b / 60) * 60);
  }
  return { lo, hi, hours: Array.from({ length: (hi - lo) / 60 + 1 }, (_, i) => lo / 60 + i) };
}
