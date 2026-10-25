package com.humano.dto.payroll.response;

import java.math.BigDecimal;

/**
 * A payroll run's totals in one currency: the run's reporting currency, or one of the currencies its
 * employees are paid in when it has none. Amounts in different currencies are never added together.
 */
public record PayrollCurrencyTotals(
    String currencyCode,
    int employeeCount,
    BigDecimal totalGross,
    BigDecimal totalDeductions,
    BigDecimal totalNet,
    BigDecimal totalEmployerCost
) {
    public static PayrollCurrencyTotals empty(String currencyCode) {
        return new PayrollCurrencyTotals(currencyCode, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public PayrollCurrencyTotals plus(PayrollCurrencyTotals other) {
        return new PayrollCurrencyTotals(
            currencyCode,
            employeeCount + other.employeeCount,
            totalGross.add(other.totalGross),
            totalDeductions.add(other.totalDeductions),
            totalNet.add(other.totalNet),
            totalEmployerCost.add(other.totalEmployerCost)
        );
    }
}
