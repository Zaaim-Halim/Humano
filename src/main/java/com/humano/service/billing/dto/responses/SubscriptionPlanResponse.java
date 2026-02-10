package com.humano.service.billing.dto.responses;

import com.humano.domain.enumeration.billing.SubscriptionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning subscription plan information.
 */
public record SubscriptionPlanResponse(
    UUID id,
    SubscriptionType subscriptionType,
    BigDecimal price,
    String displayName,
    boolean active,
    BigDecimal basePrice,
    int featureCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
