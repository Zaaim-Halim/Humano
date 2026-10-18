/** `com.humano.domain.enumeration.hr.EmployeeProcessStatus` — onboarding/offboarding lifecycle. */
export enum EmployeeProcessStatus {
  PLANNED = 'PLANNED',
  IN_PROGRESS = 'IN_PROGRESS',
  DELAYED = 'DELAYED',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
}

/** Statuses in which tasks can still be completed (the backend rejects the rest). */
export const ACTIVE_PROCESS_STATUSES: readonly EmployeeProcessStatus[] = [
  EmployeeProcessStatus.PLANNED,
  EmployeeProcessStatus.IN_PROGRESS,
  EmployeeProcessStatus.DELAYED,
];
