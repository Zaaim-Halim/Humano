package com.humano.dto.demo;

import com.humano.domain.enumeration.billing.BillingCycle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Turning a demo into a customer: the workspace (and its data) stays, it gets a subscription on
 * {@code planId}, optionally starting with a trial.
 */
public record ConvertDemoRequest(
    @NotNull UUID planId,
    @NotNull BillingCycle billingCycle,
    @Min(0) @Max(90) Integer trialDays,
    @Size(max = 50) String couponCode
) {}
