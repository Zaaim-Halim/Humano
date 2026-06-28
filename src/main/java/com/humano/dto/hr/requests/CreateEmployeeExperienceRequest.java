package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeExperience record.
 */
public record CreateEmployeeExperienceRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String company,
    String position,
    LocalDate startDate,
    LocalDate endDate
) {}
