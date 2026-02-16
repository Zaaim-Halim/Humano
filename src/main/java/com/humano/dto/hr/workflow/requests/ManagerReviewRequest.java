package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for submitting a manager review.
 */
public record ManagerReviewRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Rating is required") @Min(1) @Max(5) Integer rating,

    String strengths,

    String areasForImprovement,

    String managerComments,

    String developmentRecommendations,

    boolean recommendForPromotion,

    String promotionJustification
) {}
