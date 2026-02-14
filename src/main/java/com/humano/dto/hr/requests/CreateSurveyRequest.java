package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO record for creating a new survey.
 */
public record CreateSurveyRequest(
    @NotBlank(message = "Survey title is required") @Size(max = 255, message = "Title must not exceed 255 characters") String title,

    @Size(max = 2000, message = "Description must not exceed 2000 characters") String description,

    LocalDate startDate,

    LocalDate endDate
) {}
