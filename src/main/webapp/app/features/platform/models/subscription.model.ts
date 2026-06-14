import { AuditFields } from 'app/core/api';

import { BillingCycle } from './enums/billing-cycle.enum';
import { SubscriptionStatus } from './enums/subscription-status.enum';

/** `GET /api/billing/subscriptions/{id}` and `GET /api/billing/me/current-subscription`. */
export interface Subscription extends AuditFields {
  id: string;
  tenantId: string;
  tenantName: string | null;
  subscriptionPlanId: string | null;
  subscriptionPlanName: string | null;
  startDate: string | null;
  endDate: string | null;
  status: SubscriptionStatus;
  autoRenew: boolean | null;
  billingCycle: BillingCycle | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean | null;
  trialStart: string | null;
  trialEnd: string | null;
}

export interface CreateSubscriptionRequest {
  tenantId: string;
  subscriptionPlanId: string;
  billingCycle: BillingCycle;
  autoRenew?: boolean;
  /** ISO instant. */
  trialEnd?: string;
  couponCode?: string;
}

export interface UpdateSubscriptionRequest {
  subscriptionPlanId?: string;
  billingCycle?: BillingCycle;
  autoRenew?: boolean;
  status?: SubscriptionStatus;
  cancelAtPeriodEnd?: boolean;
}
