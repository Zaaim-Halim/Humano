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
    /** Set while a trial that ended without a plan leaves the workspace read-only: when it gets suspended. */
    Instant readOnlyUntil,
    /** What its add-ons (extra storage) add per month, on top of the plan. */
    java.math.BigDecimal addonsMonthly,
    /** The coupon taken off its invoices from the next one, re-checked at each invoice; null when none. */
    String couponCode,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
