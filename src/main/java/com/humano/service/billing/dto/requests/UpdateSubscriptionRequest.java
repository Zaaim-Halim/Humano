package com.humano.service.billing.dto.requests;

import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import java.util.UUID;

/**
 * DTO record for updating an existing subscription.
 */
public record UpdateSubscriptionRequest(
    UUID subscriptionPlanId,

    BillingCycle billingCycle,

    Boolean autoRenew,

    SubscriptionStatus status,

    Boolean cancelAtPeriodEnd
) {}
