import { AuditFields } from 'app/core/api';

import { OrganizationalUnitType } from './enums/organizational-unit-type.enum';

/** `GET /api/hr/organizational-units` (list) and `/{id}` (detail). */
export interface OrganizationalUnit extends AuditFields {
  id: string;
  name: string;
  type: OrganizationalUnitType;
  /** Materialised path, e.g. `Root / Division / Team`. */
  path: string | null;
  parentUnitId: string | null;
  parentUnitName: string | null;
  managerId: string | null;
  managerName: string | null;
  employeeCount: number;
  subUnitCount: number;
}

export interface CreateOrganizationalUnitRequest {
  name: string;
  type: OrganizationalUnitType;
  parentUnitId?: string;
  managerId?: string;
}

export interface UpdateOrganizationalUnitRequest {
  name?: string;
  type?: OrganizationalUnitType;
  parentUnitId?: string;
  managerId?: string;
}
