package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for submitting a self-assessment.
 */
public record SelfAssessmentRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Self rating is required") @Min(1) @Max(5) Integer selfRating,

    String achievements,

    String challenges,

    String developmentGoals,

    List<String> keyAccomplishments,

    String additionalComments
) {}
