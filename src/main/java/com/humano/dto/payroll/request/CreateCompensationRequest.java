package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.Basis;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a compensation record.
 */
public record CreateCompensationRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Position ID is required") UUID positionId,

    @NotNull(message = "Currency ID is required") UUID currencyId,

    @NotNull(message = "Base amount is required") @Positive(message = "Base amount must be positive") BigDecimal baseAmount,

    @NotNull(message = "Basis is required") Basis basis,

    @NotNull(message = "Effective from date is required") LocalDate effectiveFrom,

    LocalDate effectiveTo
) {}
