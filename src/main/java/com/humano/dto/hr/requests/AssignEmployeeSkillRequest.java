package com.humano.dto.hr.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for assigning a skill to an employee.
 */
public record AssignEmployeeSkillRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Skill ID is required") UUID skillId,

    @NotNull(message = "Proficiency level is required")
    @Min(value = 1, message = "Proficiency level must be at least 1")
    @Max(value = 5, message = "Proficiency level must be at most 5")
    Integer proficiencyLevel,

    LocalDate acquisitionDate,

    LocalDate expiryDate,

    @Size(max = 1000, message = "Notes must not exceed 1000 characters") String notes
) {}
