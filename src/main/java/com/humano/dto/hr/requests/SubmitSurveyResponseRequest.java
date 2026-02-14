package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for submitting a survey response.
 */
public record SubmitSurveyResponseRequest(
    @NotNull(message = "Survey ID is required") UUID surveyId,

    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Response is required") String response
) {}
