package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.DeductionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a deduction for an employee.
 */
public record CreateDeductionRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Deduction type is required") DeductionType type,

    @DecimalMin(value = "0.0", message = "Amount cannot be negative") BigDecimal amount,

    @DecimalMin(value = "0.0", message = "Percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Percentage cannot exceed 100%")
    BigDecimal percentage,

    UUID currencyId,

    @NotNull(message = "Effective from date is required") LocalDate effectiveFrom,

    LocalDate effectiveTo,

    String description,

    boolean recurring
) {
    public CreateDeductionRequest {
        if (amount == null && percentage == null) {
            throw new IllegalArgumentException("Either amount or percentage must be provided");
        }
    }
}
