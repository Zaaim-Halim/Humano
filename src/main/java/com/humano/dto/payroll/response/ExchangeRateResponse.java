package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for exchange rate details.
 */
public record ExchangeRateResponse(
    UUID id,
    UUID fromCurrencyId,
    String fromCurrencyCode,
    String fromCurrencyName,
    UUID toCurrencyId,
    String toCurrencyCode,
    String toCurrencyName,
    BigDecimal rate,
    BigDecimal inverseRate,
    LocalDate date
) {}
