package com.humano.service.tenant.dto.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning organization information.
 */
public record OrganizationResponse(
    UUID id,
    String name,
    UUID tenantId,
    String tenantName,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
