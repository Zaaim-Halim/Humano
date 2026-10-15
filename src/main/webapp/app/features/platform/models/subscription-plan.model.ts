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
  /** One-line pitch on the public pricing page. */
  description: string | null;
  /** Highlighted ("most popular") on the public pricing page. */
  featured: boolean;
  /** Listed on the public pricing page. */
  publicListed: boolean;
}

export interface CreateSubscriptionPlanRequest {
  subscriptionType: SubscriptionType;
  price: number;
  displayName?: string;
  basePrice?: number;
  description?: string;
  featured?: boolean;
  publicListed?: boolean;
}

export interface UpdateSubscriptionPlanRequest {
  subscriptionType?: SubscriptionType;
  price?: number;
  displayName?: string;
  basePrice?: number;
  active?: boolean;
  description?: string;
  featured?: boolean;
  publicListed?: boolean;
}
