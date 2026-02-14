package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning survey information.
 */
public record SurveyResponse(
    UUID id,
    String title,
    String description,
    LocalDate startDate,
    LocalDate endDate,
    int responseCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
