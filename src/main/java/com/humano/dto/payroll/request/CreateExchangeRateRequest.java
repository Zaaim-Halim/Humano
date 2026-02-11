package com.humano.dto.payroll.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating an exchange rate.
 */
public record CreateExchangeRateRequest(
    @NotNull(message = "Source currency ID is required") UUID fromCurrencyId,

    @NotNull(message = "Target currency ID is required") UUID toCurrencyId,

    @NotNull(message = "Rate is required") @DecimalMin(value = "0.000001", message = "Rate must be positive") BigDecimal rate,

    @NotNull(message = "Date is required") LocalDate date,

    boolean replaceExisting
) {}
