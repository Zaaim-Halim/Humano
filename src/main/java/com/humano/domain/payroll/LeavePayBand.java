package com.humano.domain.payroll;

import java.math.BigDecimal;

/**
 * A stretch of an absence paid differently from the rest, as part of a {@link LeaveTypeRule}.
 * <p>
 * Days are counted in calendar days from the absence's first day, including days that fell in an
 * earlier pay period: German sick pay, for example, is a band from day 1 to day 42 with no deduction,
 * and the rule's own percentage (100%) applies from day 43, when the health insurer pays instead.
 *
 * @param fromDay             first day of the absence the band covers (1 = the first day)
 * @param toDay               last day it covers, or null for every day from {@code fromDay} on
 * @param deductionPercentage share of the daily salary not paid on those days, 0 to 100
 */
public record LeavePayBand(int fromDay, Integer toDay, BigDecimal deductionPercentage) {
    /** Whether the absence's {@code day} (1 = its first day) falls in this band. */
    public boolean covers(int day) {
        return day >= fromDay && (toDay == null || day <= toDay);
    }
}
