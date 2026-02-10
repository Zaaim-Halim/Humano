package com.humano.service.hr.dto.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning position information.
 */
public record PositionResponse(
    UUID id,
    String name,
    String description,
    String level,
    UUID unitId,
    String unitName,
    UUID parentPositionId,
    String parentPositionName,
    int employeeCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
