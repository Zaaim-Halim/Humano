package com.humano.dto.hr.requests;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO record for updating an existing survey.
 */
public record UpdateSurveyRequest(
    @Size(max = 255, message = "Title must not exceed 255 characters") String title,

    @Size(max = 2000, message = "Description must not exceed 2000 characters") String description,

    LocalDate startDate,

    LocalDate endDate
) {}
