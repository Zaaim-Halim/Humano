package com.humano.service.billing.dto.responses;

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
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
