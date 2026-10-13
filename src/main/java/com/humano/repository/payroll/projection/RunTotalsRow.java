package com.humano.repository.payroll.projection;

import com.humano.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregated payroll results of one run in one currency — totals use the reporting-currency
 * figures when the run has a reporting currency, the native ones otherwise.
 */
public record RunTotalsRow(
    UUID runId,
    CurrencyCode currency,
    long employees,
    BigDecimal gross,
    BigDecimal deductions,
    BigDecimal net,
    BigDecimal employerCost
) {}
