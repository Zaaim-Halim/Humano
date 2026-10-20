package com.humano.domain.enumeration.payroll;

/**
 * @author halimzaaim
 */// Frequency enum
public enum Frequency {
    MONTHLY(12),
    BIWEEKLY(26),
    WEEKLY(52),
    SEMIMONTHLY(24);

    private final int periodsPerYear;

    Frequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    /** Number of pay periods in a year, used to annualise per-period income for ANNUAL tax brackets. */
    public int periodsPerYear() {
        return periodsPerYear;
    }
}
