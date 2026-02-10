package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO record for updating an employee's skill record.
 */
public record UpdateEmployeeSkillRequest(
    @Min(value = 1, message = "Proficiency level must be at least 1")
    @Max(value = 5, message = "Proficiency level must be at most 5")
    Integer proficiencyLevel,

    LocalDate acquisitionDate,

    LocalDate expiryDate,

    @Size(max = 1000, message = "Notes must not exceed 1000 characters") String notes,

    Boolean isVerified
) {}
