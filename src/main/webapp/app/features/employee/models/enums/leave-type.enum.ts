/** `com.humano.domain.enumeration.hr.LeaveType`. */
export enum LeaveType {
  SICK = 'SICK',
  VACATION = 'VACATION',
  MATERNITY = 'MATERNITY',
  UNPAID = 'UNPAID',
}

/** Colour per leave type (calendars, charts, timelines). */
export const LEAVE_TYPE_COLOR: Record<string, string> = {
  VACATION: 'var(--brand)',
  SICK: 'var(--danger-solid)',
  MATERNITY: 'var(--violet-500)',
  UNPAID: 'var(--slate-400)',
};

/** Icon per leave type. */
export const LEAVE_TYPE_ICON: Record<string, string> = {
  VACATION: 'palmtree',
  SICK: 'thermometer',
  MATERNITY: 'baby',
  UNPAID: 'calendar-off',
};
