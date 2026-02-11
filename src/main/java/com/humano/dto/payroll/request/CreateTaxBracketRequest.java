package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.TaxCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a tax bracket.
 */
public record CreateTaxBracketRequest(
    @NotNull(message = "Country ID is required") UUID countryId,

    @NotNull(message = "Tax code is required") TaxCode taxCode,

    @NotNull(message = "Lower bound is required") @DecimalMin(value = "0.0", message = "Lower bound cannot be negative") BigDecimal lower,

    @NotNull(message = "Upper bound is required") BigDecimal upper,

    @NotNull(message = "Rate is required")
    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    @DecimalMax(value = "1.0", message = "Rate cannot exceed 100%")
    BigDecimal rate,

    @DecimalMin(value = "0.0", message = "Fixed part cannot be negative") BigDecimal fixedPart,

    @NotNull(message = "Valid from date is required") LocalDate validFrom,

    LocalDate validTo
) {}
