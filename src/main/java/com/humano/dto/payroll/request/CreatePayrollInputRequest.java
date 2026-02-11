package com.humano.dto.payroll.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a payroll input entry.
 */
public record CreatePayrollInputRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Period ID is required") UUID periodId,

    @NotNull(message = "Component ID is required") UUID componentId,

    @DecimalMin(value = "0.0", message = "Quantity cannot be negative") BigDecimal quantity,

    @DecimalMin(value = "0.0", message = "Rate cannot be negative") BigDecimal rate,

    @DecimalMin(value = "0.0", message = "Amount cannot be negative") BigDecimal amount,

    String source,

    String metadata,

    boolean replaceExisting
) {}
