package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new performance review.
 */
public record CreatePerformanceReviewRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Reviewer ID is required") UUID reviewerId,

    @NotNull(message = "Review date is required") LocalDate reviewDate,

    @Size(max = 2000, message = "Comments must not exceed 2000 characters") String comments,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must not exceed 5")
    Integer rating
) {}
