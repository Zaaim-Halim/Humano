package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning subscription information.
 */
public record SubscriptionResponse(
    UUID id,
    UUID tenantId,
    String tenantName,
    UUID subscriptionPlanId,
    String subscriptionPlanName,
    Instant startDate,
    Instant endDate,
    SubscriptionStatus status,
    Boolean autoRenew,
    BillingCycle billingCycle,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Boolean cancelAtPeriodEnd,
    Instant trialStart,
    Instant trialEnd,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
