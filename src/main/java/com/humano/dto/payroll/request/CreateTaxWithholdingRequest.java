package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.TaxType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a tax withholding configuration.
 */
public record CreateTaxWithholdingRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Tax type is required") TaxType type,

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Rate cannot exceed 100%")
    BigDecimal rate,

    @NotNull(message = "Effective from date is required") LocalDate effectiveFrom,

    LocalDate effectiveTo,

    @NotNull(message = "Tax authority is required")
    @Size(min = 2, max = 100, message = "Tax authority must be between 2 and 100 characters")
    String taxAuthority,

    @Size(max = 50, message = "Tax identifier cannot exceed 50 characters") String taxIdentifier
) {}
