package com.humano.dto.payroll.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for generating payroll periods for a calendar.
 */
public record GeneratePayrollPeriodsRequest(
    @NotNull(message = "Calendar ID is required") UUID calendarId,

    @NotNull(message = "Start date is required") LocalDate startDate,

    @NotNull(message = "End date is required") LocalDate endDate,

    @Min(value = 1, message = "Payment day offset must be at least 1") int paymentDayOffset,

    boolean skipExistingPeriods
) {}
