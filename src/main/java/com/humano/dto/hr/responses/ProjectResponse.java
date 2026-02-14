package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO record for returning project information.
 */
public record ProjectResponse(
    UUID id,
    String name,
    String description,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int timesheetCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
