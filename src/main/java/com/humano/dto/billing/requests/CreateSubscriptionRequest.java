package com.humano.dto.billing.requests;

import com.humano.domain.enumeration.billing.BillingCycle;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    Instant trialEnd,

    /**
     * P4.5 — Optional coupon code applied to this subscription. Validated at
     * creation (throws HTTP 400 on unknown / inactive / expired / exhausted
     * codes) and snapshotted onto {@code Subscription.couponCode} so the first
     * renewal invoice can re-use it. Redemption (the {@code timesRedeemed}
     * bump) happens at invoice issuance via
     * {@code CouponService.applyToAmount}, not here — pre-validation only.
     */
    @Size(max = 50, message = "Coupon code cannot exceed 50 characters") String couponCode
) {}
