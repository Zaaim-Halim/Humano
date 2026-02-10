package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for updating an existing timesheet entry.
 */
public record UpdateTimesheetRequest(
    LocalDate date,

    @DecimalMin(value = "0.0", message = "Hours worked must be non-negative") BigDecimal hoursWorked,

    UUID projectId
) {}
