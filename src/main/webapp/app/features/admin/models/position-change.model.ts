/** What kind of move a position change is. */
export type PositionChangeType = 'PROMOTION' | 'DEMOTION' | 'TRANSFER' | 'OTHER';
export type PositionChangeStatus = 'PENDING' | 'APPLIED' | 'CANCELLED';

export interface PositionChangeRef {
  id: string;
  name: string;
}

/**
 * One change in someone's career — `GET /api/hr/employees/{id}/position-changes` and
 * `GET /api/me/position-history`. A `new*` side is null when that part did not change; salaries
 * are null unless the change set one and the reader may see pay.
 */
export interface PositionChange {
  id: string;
  changeType: PositionChangeType;
  status: PositionChangeStatus;
  effectiveDate: string;
  reason: string | null;
  oldPosition: PositionChangeRef | null;
  newPosition: PositionChangeRef | null;
  oldDepartment: PositionChangeRef | null;
  newDepartment: PositionChangeRef | null;
  oldUnit: PositionChangeRef | null;
  newUnit: PositionChangeRef | null;
  oldManager: PositionChangeRef | null;
  newManager: PositionChangeRef | null;
  oldSalary: number | null;
  newSalary: number | null;
  recordedBy: string | null;
  recordedAt: string | null;
}

/** `POST /api/hr/employees/{id}/position-changes`: left-out fields stay as they are. */
export interface PositionChangeRequest {
  changeType: PositionChangeType;
  effectiveDate: string;
  newPositionId?: string;
  newDepartmentId?: string;
  newSalary?: number;
  reason: string;
}
