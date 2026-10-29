package com.humano.dto.billing.requests;

import com.humano.domain.enumeration.billing.BillingCycle;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A customer moving to another plan (or billing cycle), or picking one again after cancelling,
 * optionally with a coupon code for the chosen plan and cycle.
 */
public record ChangeMyPlanRequest(
    @NotNull UUID planId,
    @NotNull BillingCycle billingCycle,
    @Size(max = 50, message = "Coupon code cannot exceed 50 characters") String couponCode
) {}
