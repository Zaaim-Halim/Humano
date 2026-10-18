/** Codes of the features the app enforces (`FeatureCodes` on the server). */
export const PlanFeature = {
  EMPLOYEES: 'EMPLOYEES',
  ADMIN_SEATS: 'ADMIN_SEATS',
  PAYROLL: 'PAYROLL',
  TIMESHEETS: 'TIMESHEETS',
  APPROVAL_CHAINS: 'APPROVAL_CHAINS',
  COMPENSATION: 'COMPENSATION',
  PERFORMANCE: 'PERFORMANCE',
  TRAINING: 'TRAINING',
  /** Storage, in GB (the catalog's "Document storage"). */
  STORAGE: 'DOCUMENTS',
} as const;

/** The enforced modules (yes/no features), in the order the plan screen lists them. */
export const PLAN_MODULES: string[] = [
  PlanFeature.PAYROLL,
  PlanFeature.TIMESHEETS,
  PlanFeature.APPROVAL_CHAINS,
  PlanFeature.COMPENSATION,
  PlanFeature.PERFORMANCE,
  PlanFeature.TRAINING,
];

/** The counted features with live usage, for the meters. */
export const PLAN_COUNTERS: string[] = [PlanFeature.EMPLOYEES, PlanFeature.ADMIN_SEATS, PlanFeature.STORAGE];

/** One feature of `GET /api/billing/me/entitlements`. `limit` null = unlimited; `used` null = not counted. */
export interface FeatureEntitlement {
  code: string;
  featureId: string;
  name: string;
  unit: string | null;
  included: boolean;
  limit: number | null;
  used: number | null;
}

/** `GET /api/billing/me/entitlements` — `planName` null when no plan applies (nothing is limited). */
export interface Entitlements {
  planName: string | null;
  features: FeatureEntitlement[];
}

/** Whether the plan includes `code` (features the catalog does not sell restrict nothing). */
export function planIncludes(e: Entitlements | null, code: string): boolean {
  const f = e?.features.find(x => x.code === code);
  return !f || f.included;
}

/** `GET/PUT /api/billing/me/addons/storage` — extra storage bought on top of the plan, billed monthly. */
export interface StorageAddon {
  /** The plan can have extra storage. */
  available: boolean;
  /** GB in one pack. */
  packGb: number;
  /** Monthly price of a new pack. */
  offerMonthlyPrice: number;
  currency: string;
  /** Packs held now. */
  packs: number;
  /** Monthly price of the packs held (what they were bought at), null when none. */
  packMonthlyPrice: number | null;
  monthlyTotal: number;
  /** Plan types (`PublicPlan.type`) that can have it. */
  availableOnPlanTypes: string[];
}

/** One customer's add-on, as the owner console lists them (`GET /api/platform/billing/addons`). */
export interface AddonRow {
  id: string;
  tenantId: string;
  tenantName: string;
  subdomain: string;
  planName: string;
  billingCycle: 'MONTHLY' | 'YEARLY';
  subscriptionStatus: string;
  featureCode: string;
  quantity: number;
  unitSize: number;
  unitPrice: number;
  monthlyTotal: number;
  billedUntil: string | null;
  since: string | null;
}

/** A customer's storage in the owner console: packs, limit with them, usage (null when unreadable). */
export interface CustomerStorage {
  planName: string | null;
  addon: StorageAddon;
  usedGb: number | null;
  limitGb: number | null;
}
