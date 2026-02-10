package com.humano.service.hr.dto.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new timesheet entry.
 */
public record CreateTimesheetRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Date is required") LocalDate date,

    @NotNull(message = "Hours worked is required")
    @DecimalMin(value = "0.0", message = "Hours worked must be non-negative")
    BigDecimal hoursWorked,

    UUID projectId
) {}
