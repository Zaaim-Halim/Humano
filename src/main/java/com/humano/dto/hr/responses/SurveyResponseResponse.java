package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO record for returning survey response information.
 */
public record SurveyResponseResponse(
    UUID id,
    UUID surveyId,
    String surveyTitle,
    String response,
    LocalDateTime submittedAt,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
