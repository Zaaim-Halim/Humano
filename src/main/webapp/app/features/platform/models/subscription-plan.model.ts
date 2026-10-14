import { AuditFields } from 'app/core/api';

import { SubscriptionType } from './enums/subscription-type.enum';

/** `GET /api/billing/plans` (list) and `/{id}` (detail). `basePrice` (else `price`) is the monthly price. */
export interface SubscriptionPlan extends AuditFields {
  id: string;
  subscriptionType: SubscriptionType;
  price: number;
  displayName: string | null;
  active: boolean;
  basePrice: number | null;
  featureCount: number;
}

export interface CreateSubscriptionPlanRequest {
  subscriptionType: SubscriptionType;
  price: number;
  displayName?: string;
  basePrice?: number;
}

export interface UpdateSubscriptionPlanRequest {
  subscriptionType?: SubscriptionType;
  price?: number;
  displayName?: string;
  basePrice?: number;
  active?: boolean;
}
