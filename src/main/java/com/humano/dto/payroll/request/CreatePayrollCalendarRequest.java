package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.Frequency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a payroll calendar.
 */
public record CreatePayrollCalendarRequest(
    @NotNull(message = "Name is required") @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters") String name,

    @NotNull(message = "Frequency is required") Frequency frequency,

    @NotNull(message = "Timezone is required") String timezone,

    boolean active
) {}
