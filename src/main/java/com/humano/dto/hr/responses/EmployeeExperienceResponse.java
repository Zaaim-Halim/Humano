package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeExperience record.
 */
public record EmployeeExperienceResponse(
    UUID id,
    UUID employeeId,
    String company,
    String position,
    LocalDate startDate,
    LocalDate endDate,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
