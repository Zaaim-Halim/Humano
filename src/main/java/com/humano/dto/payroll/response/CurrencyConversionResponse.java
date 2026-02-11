package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for currency conversion result.
 */
public record CurrencyConversionResponse(
    BigDecimal originalAmount,
    String fromCurrencyCode,
    BigDecimal convertedAmount,
    String toCurrencyCode,
    BigDecimal exchangeRate,
    LocalDate rateDate,
    UUID exchangeRateId
) {}
