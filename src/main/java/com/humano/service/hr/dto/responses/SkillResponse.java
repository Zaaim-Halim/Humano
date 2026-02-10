package com.humano.service.hr.dto.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning skill information.
 */
public record SkillResponse(
    UUID id,
    String name,
    String description,
    String category,
    Boolean requiresCertification,
    int employeeCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
