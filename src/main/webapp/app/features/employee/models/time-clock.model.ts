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

/** `TeamAttendance.Presence` — where someone is on the day. */
export type TeamPresence = 'WORKING' | 'ON_BREAK' | 'DONE' | 'MISSING_CLOCK_OUT' | 'ON_LEAVE' | 'NOT_IN' | 'ABSENT' | 'OFF';

export interface TeamAttendanceRow {
  employeeId: string;
  name: string;
  jobTitle: string | null;
  imageUrl: string | null;
  unitName: string | null;
  managerName: string | null;
  presence: TeamPresence;
  /** Set only when the viewer may see leave types. */
  leaveType: string | null;
  day: TimeClockDay | null;
}

/** `TeamAttendance` — `GET /api/me/attendance/team`. `NONE`: nobody to show (hide the view). */
export interface TeamAttendance {
  scope: 'ALL' | 'REPORTS' | 'NONE';
  date: string;
  rows: TeamAttendanceRow[];
}
