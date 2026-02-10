package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO record for updating an existing training program.
 */
public record UpdateTrainingRequest(
    @Size(max = 255, message = "Training name must not exceed 255 characters") String name,

    String provider,

    LocalDate startDate,

    LocalDate endDate,

    @Size(max = 1000, message = "Description must not exceed 1000 characters") String description,

    String location,

    String certificate
) {}
