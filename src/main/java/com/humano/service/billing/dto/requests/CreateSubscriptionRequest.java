package com.humano.service.billing.dto.requests;

import com.humano.domain.enumeration.billing.BillingCycle;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for creating a new subscription.
 */
public record CreateSubscriptionRequest(
    @NotNull(message = "Tenant ID is required") UUID tenantId,

    @NotNull(message = "Subscription plan ID is required") UUID subscriptionPlanId,

    @NotNull(message = "Billing cycle is required") BillingCycle billingCycle,

    Boolean autoRenew,

    Instant trialEnd
) {}
