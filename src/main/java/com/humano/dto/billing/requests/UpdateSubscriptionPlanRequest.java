package com.humano.dto.billing.requests;

import com.humano.domain.enumeration.billing.SubscriptionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO record for updating an existing subscription plan.
 */
public record UpdateSubscriptionPlanRequest(
    SubscriptionType subscriptionType,

    @DecimalMin(value = "0.0", message = "Price cannot be negative") BigDecimal price,

    @Size(max = 100, message = "Display name cannot exceed 100 characters") String displayName,

    @DecimalMin(value = "0.0", message = "Base price cannot be negative") BigDecimal basePrice,

    Boolean active
) {}
