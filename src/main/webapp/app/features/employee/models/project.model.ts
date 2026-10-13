import { AuditFields } from 'app/core/api';

/** `GET /api/hr/projects` row. Times are ISO local date-times. */
export interface Project extends AuditFields {
  id: string;
  name: string;
  description: string | null;
  startTime: string | null;
  endTime: string | null;
  timesheetCount: number;
}
