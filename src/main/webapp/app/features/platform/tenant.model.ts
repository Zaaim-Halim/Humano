import { AuditFields } from 'app/core/api';

import { TenantStatus } from './enums/tenant-status.enum';

/** `GET /api/platform/tenants` (list) and the `tenant` inside detail. */
export interface Tenant extends AuditFields {
  id: string;
  name: string;
  domain: string | null;
  subdomain: string | null;
  logo: string | null;
  /** IANA/Java TimeZone id. */
  timezone: string | null;
  status: TenantStatus;
  bookingPolicies: string | null;
  hrPolicies: string | null;
  subscriptionPlanId: string | null;
  subscriptionPlanName: string | null;
  organizationCount: number;
}

/** Live Hikari pool stats attached to tenant detail (best-effort, may be null). */
export type TenantPoolStats = Record<string, number | string | boolean | null>;

/** `GET /api/platform/tenants/{id}` — tenant + live DB pool stats. */
export interface TenantDetail {
  tenant: Tenant;
  poolStats: TenantPoolStats | null;
}
