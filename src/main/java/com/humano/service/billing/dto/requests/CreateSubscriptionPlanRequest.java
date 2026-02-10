package com.humano.service.billing.dto.requests;

import com.humano.domain.enumeration.billing.SubscriptionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO record for creating a new subscription plan.
 */
public record CreateSubscriptionPlanRequest(
    @NotNull(message = "Subscription type is required") SubscriptionType subscriptionType,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    BigDecimal price,

    @Size(max = 100, message = "Display name cannot exceed 100 characters") String displayName,

    @DecimalMin(value = "0.0", inclusive = true, message = "Base price cannot be negative") BigDecimal basePrice
) {}
