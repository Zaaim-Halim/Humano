/** `TimeClockDay.State`. */
export type TimeClockState = 'NOT_STARTED' | 'WORKING' | 'ON_BREAK' | 'DONE';

/** `EventType` — what a clock event is about. */
export type ClockEventType = 'CHECK_IN' | 'CHECK_OUT' | 'LUNCH_BREAK' | 'MEETING' | 'OTHER';

/** `EventAction`. */
export type ClockEventAction = 'CHECK_IN' | 'CHECK_OUT' | 'BREAK_START' | 'BREAK_END' | 'OTHER';

export interface ClockEvent {
  type: ClockEventType;
  action: ClockEventAction;
  /** `HH:mm:ss` in the company timezone. */
  time: string;
  description: string | null;
}

/** `TimeClockDay` — one day on the signed-in employee's time clock (`/api/me/attendance`). */
export interface TimeClockDay {
  attendanceId: string | null;
  date: string;
  status: string | null;
  state: TimeClockState;
  checkIn: string | null;
  checkOut: string | null;
  workedMinutes: number;
  breakMinutes: number;
  events: ClockEvent[];
}

export interface TimeClockRequest {
  remote?: boolean;
  breakType?: ClockEventType;
  note?: string;
}
