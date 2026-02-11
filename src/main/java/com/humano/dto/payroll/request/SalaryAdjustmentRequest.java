package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.Basis;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for adjusting an employee's salary.
 * Supports both absolute amount and percentage-based adjustments.
 */
public record SalaryAdjustmentRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    BigDecimal newAmount,

    BigDecimal adjustmentPercentage,

    Basis newBasis,

    UUID newCurrencyId,

    @NotNull(message = "Effective from date is required") LocalDate effectiveFrom,

    @NotNull(message = "Reason for adjustment is required") String reason
) {
    public SalaryAdjustmentRequest {
        if (newAmount == null && adjustmentPercentage == null) {
            throw new IllegalArgumentException("Either newAmount or adjustmentPercentage must be provided");
        }
        if (newAmount != null && adjustmentPercentage != null) {
            throw new IllegalArgumentException("Cannot specify both newAmount and adjustmentPercentage");
        }
    }
}
