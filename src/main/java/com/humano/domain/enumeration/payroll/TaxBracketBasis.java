package com.humano.domain.enumeration.payroll;

/**
 * The income period a {@link com.humano.domain.payroll.TaxBracket} set's thresholds are expressed in.
 * <ul>
 *   <li>ANNUAL: thresholds are yearly amounts, as most tax authorities publish them. Payroll
 *   annualises the period's taxable income ({@code × periodsPerYear} of the calendar's
 *   {@link Frequency}), applies the brackets, and divides the tax back to the period — so one
 *   bracket set serves monthly, semi-monthly, biweekly and weekly calendars alike.</li>
 *   <li>PER_PERIOD: thresholds are amounts per pay period and are applied to the period's taxable
 *   income as-is. Only correct for calendars of the frequency the table was written for.</li>
 * </ul>
 */
public enum TaxBracketBasis {
    ANNUAL,
    PER_PERIOD,
}
