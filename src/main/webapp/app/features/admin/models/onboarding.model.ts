import { EmployeeProcessStatus } from './enums/employee-process-status.enum';

/**
 * Row projection — `GET /api/hr/workflows/onboarding` (`Page`). Only active
 * (planned, in-progress or delayed) processes are listed. Dates are
 * `YYYY-MM-DD` strings on the wire.
 */
export interface EmployeeProcessSummary {
  processId: string;
  processType: 'ONBOARDING' | 'OFFBOARDING';
  status: EmployeeProcessStatus;
  employeeId: string;
  employeeName: string;
  startDate: string;
  dueDate: string | null;
  completionDate: string | null;
  completionPercentage: number;
  totalTasks: number;
  completedTasks: number;
  /** Open tasks whose due date has passed. */
  overdueTasks: number;
}

/** One checklist item. `assignedTo*` is null for tasks owned by HR/IT rather than a person. */
export interface OnboardingTask {
  taskId: string;
  title: string;
  description: string | null;
  dueDate: string | null;
  completed: boolean;
  completionDate: string | null;
  assignedToId: string | null;
  assignedToName: string | null;
  completionNotes: string | null;
}

/** Readiness flags derived from the employee's real records. */
export interface OnboardingProgress {
  profileCreated: boolean;
  departmentAssigned: boolean;
  benefitsSetup: boolean;
  trainingsAssigned: boolean;
  documentsUploaded: boolean;
  systemAccessGranted: boolean;
  totalTasks: number;
  completedTasks: number;
}

/**
 * Full process — `GET /api/hr/workflows/onboarding/{processId}`, also returned by
 * initiate and complete-task. `tasks` arrive in no particular order.
 */
export interface OnboardingProcess {
  processId: string;
  workflowId: string | null;
  employeeId: string;
  employeeName: string;
  employeeEmail: string | null;
  workflowStatus: string;
  processStatus: EmployeeProcessStatus;
  currentState: string | null;
  startDate: string;
  dueDate: string | null;
  completionPercentage: number;
  tasks: OnboardingTask[];
  progress: OnboardingProgress;
  createdDate: string | null;
  lastModifiedDate: string | null;
}

/** Body for `POST /api/hr/workflows/onboarding`. */
export interface InitiateOnboardingRequest {
  employeeId: string;
  startDate?: string;
  /** Finish-by date; task due dates are spread between the start and this day (default: 30 days after the start). */
  targetDate?: string;
  notes?: string;
}

/** Body for `POST /api/hr/workflows/onboarding/{processId}/tasks/complete`. */
export interface CompleteTaskRequest {
  taskId: string;
  completionNotes?: string;
  successful: boolean;
}

/** `com.humano.domain.enumeration.hr.OnboardingTaskOwner` — who a checklist task is assigned to. */
export type OnboardingTaskOwner = 'NEW_HIRE' | 'MANAGER' | 'HR';

/** One task of the company checklist. `dueDay` is days after the start on a default-length onboarding. */
export interface OnboardingChecklistTask {
  title: string;
  description: string | null;
  dueDay: number;
  owner: OnboardingTaskOwner;
}

/**
 * Company onboarding policy — `GET/PUT /api/hr/workflows/onboarding-checklist`.
 * `customized` is false while the built-in checklist is in use.
 */
export interface OnboardingChecklist {
  defaultDays: number;
  customized: boolean;
  tasks: OnboardingChecklistTask[];
}

/** Body for `PUT /api/hr/workflows/onboarding-checklist` (replaces length and checklist). */
export interface UpdateOnboardingChecklistRequest {
  defaultDays: number;
  tasks: OnboardingChecklistTask[];
}

/** Local calendar date as `YYYY-MM-DD` — comparable with the API's `LocalDate` strings. */
export function localIsoDate(date = new Date()): string {
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Whole days from `from` to `to` (both `YYYY-MM-DD`), counted on local calendar dates. */
export function daysBetween(from: string, to: string): number {
  return Math.round((new Date(`${to}T00:00:00`).getTime() - new Date(`${from}T00:00:00`).getTime()) / 86400000);
}

/** `YYYY-MM-DD` that many days after `date`. */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00`);
  d.setDate(d.getDate() + days);
  return localIsoDate(d);
}
