package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.FeatureLimitType;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning feature information.
 */
public record FeatureResponse(
    UUID id,
    String name,
    String description,
    String code,
    String category,
    boolean active,
    FeatureLimitType limitType,
    String unit,
    int sortOrder,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
