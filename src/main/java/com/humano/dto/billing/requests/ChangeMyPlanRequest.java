package com.humano.dto.billing.requests;

import com.humano.domain.enumeration.billing.BillingCycle;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** A customer moving to another plan (or billing cycle), or picking one again after cancelling. */
public record ChangeMyPlanRequest(@NotNull UUID planId, @NotNull BillingCycle billingCycle) {}
