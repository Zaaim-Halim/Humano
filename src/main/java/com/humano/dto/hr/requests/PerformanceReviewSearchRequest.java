package com.humano.dto.hr.requests;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching performance reviews with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record PerformanceReviewSearchRequest(
    UUID employeeId,
    UUID reviewerId,
    LocalDate reviewDateFrom,
    LocalDate reviewDateTo,
    Integer minRating,
    Integer maxRating,
    String comments,
    String createdBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
