package com.humano.service.hr.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning performance review information.
 */
public record PerformanceReviewResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    UUID reviewerId,
    String reviewerName,
    LocalDate reviewDate,
    String comments,
    Integer rating,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
