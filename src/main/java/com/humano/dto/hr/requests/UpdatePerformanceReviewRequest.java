package com.humano.dto.hr.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * DTO record for updating an existing performance review.
 */
public record UpdatePerformanceReviewRequest(
    LocalDate reviewDate,

    String comments,

    @Min(value = 0, message = "Rating must be at least 0") @Max(value = 5, message = "Rating must be at most 5") Integer rating
) {}
