package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
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
    LocalDate createdDateTo,
    UUID cycleId,
    PerformanceReviewStatus status
) {
    /** The reviews of one employee that they may see: written outside a cycle, or shared with them. */
    public static PerformanceReviewSearchRequest sharedWith(UUID employeeId) {
        return new PerformanceReviewSearchRequest(
            employeeId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            PerformanceReviewStatus.SHARED
        );
    }
}
