package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning training program information.
 */
public record TrainingResponse(
    UUID id,
    String name,
    String provider,
    LocalDate startDate,
    LocalDate endDate,
    String description,
    String location,
    String certificate,
    int enrolledEmployeeCount,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
