/** `GET /api/platform/metrics/overview` — the owner's business at a glance. Amounts in `currency`. */
export interface PlatformOverview {
  currency: string;
  customers: {
    total: number;
    active: number;
    newLast30Days: number;
    byStatus: Record<string, number>;
    signupsByMonth: MonthValue[];
  };
  revenue: {
    mrr: number;
    arr: number;
    arpa: number;
    payingCustomers: number;
    collectedLast30Days: number;
    outstanding: number;
    outstandingInvoices: number;
    overdue: number;
    overdueInvoices: number;
    collectedByMonth: MonthValue[];
    mrrByMonth: MonthValue[];
  };
  subscriptions: {
    byStatus: Record<string, number>;
    trials: number;
    trialsEndingIn14Days: number;
    churnedLast30Days: number;
    /** Percent, one decimal. */
    churnRate: number;
  };
  /** Self-serve sign-ups confirmed in the last 30 days and how far they got. */
  selfServe: { confirmed: number; ready: number; converted: number; failed: number };
  plans: PlanBreakdown[];
  attention: AttentionItem[];
}

/** A value for a calendar month (`yyyy-MM`). */
export interface MonthValue {
  month: string;
  value: number;
}

export interface PlanBreakdown {
  planId: string;
  name: string;
  type: string;
  monthlyPrice: number;
  active: boolean;
  customers: number;
  mrr: number;
}

/** A customer needing attention: PROVISIONING_FAILED, MIGRATION_FAILED, SUBSCRIPTION_PAST_DUE, SUBSCRIPTION_SUSPENDED, INVOICE_OVERDUE. */
export interface AttentionItem {
  tenantId: string;
  tenantName: string;
  subdomain: string;
  kind: string;
  /** e.g. the overdue amount. */
  detail: string | null;
}

/** `GET /api/platform/team` — a platform staff account. */
export interface TeamMember {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  imageUrl: string | null;
  activated: boolean;
  /** Invited but hasn't set a password yet. */
  invitationPending: boolean;
  role: PlatformRole | null;
  createdDate: string | null;
}

export type PlatformRole = 'ROLE_PLATFORM_OWNER' | 'ROLE_PLATFORM_ADMIN';
export const PLATFORM_ROLE_OPTIONS: PlatformRole[] = ['ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN'];

export interface Coupon {
  id: string;
  code: string;
  type: 'PERCENT' | 'FIXED';
  discount: number;
  percentage: number | null;
  startDate: string | null;
  expiryDate: string;
  active: boolean;
  maxRedemptions: number | null;
  timesRedeemed: number | null;
  createdDate: string | null;
}

export interface CreateCouponRequest {
  code: string;
  type: 'PERCENT' | 'FIXED';
  discount: number;
  percentage?: number;
  /** ISO instant. */
  expiryDate: string;
  startDate?: string;
  maxRedemptions?: number;
}

/** A catalog feature plans can include (`BOOLEAN`: has it or not; `QUANTITY`: a limit in `unit`). */
export interface PlanFeatureDef {
  id: string;
  code: string;
  name: string;
  description: string | null;
  category: string | null;
  limitType: 'BOOLEAN' | 'QUANTITY' | 'METERED';
  unit: string | null;
  sortOrder: number;
}

/** What a plan includes of one feature: `limitValue` null on an included quantity = unlimited. */
export interface PlanFeatureCell {
  featureId: string;
  included: boolean;
  limitValue: number | null;
  note: string | null;
}

/** `GET /api/billing/plans/comparison` — catalog rows and each plan's cells. */
export interface PlanComparison {
  features: PlanFeatureDef[];
  plans: { planId: string; items: PlanFeatureCell[] }[];
}

export interface FeatureRequest {
  name: string;
  description?: string;
  category?: string;
  limitType: 'BOOLEAN' | 'QUANTITY';
  unit?: string;
  sortOrder?: number;
}
