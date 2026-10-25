package com.humano.service.payroll.template.engine;

import com.humano.service.payroll.template.model.LineSide;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Bound as {@code #ytd} while a template runs: what this template's steps produced for the employee
 * earlier in the tax year, from posted payroll runs only (the current run is excluded). A step with
 * no earlier lines sums to zero. {@link #last} reads the values {@code remember} steps kept.
 */
public final class YtdAccess {

    private final Map<String, BigDecimal> sums;

    /** @param sums keyed {@code step.SIDE.base} / {@code step.SIDE.amount} / {@code step.SIDE.periods} */
    YtdAccess(Map<String, BigDecimal> sums) {
        this.sums = sums == null ? Map.of() : sums;
    }

    /** Sum of the bases of a step's lines on {@code side} (EMPLOYEE, EMPLOYER or TAX) — e.g. wages already subject to a capped contribution. */
    public BigDecimal base(String step, String side) {
        return sums.getOrDefault(key(step, LineSide.valueOf(side), "base"), BigDecimal.ZERO);
    }

    /** How many earlier posted periods have a line from this step on {@code side}. */
    public BigDecimal periods(String step, String side) {
        return sums.getOrDefault(key(step, LineSide.valueOf(side), "periods"), BigDecimal.ZERO);
    }

    /** Sum of the amounts of a step's lines on {@code side}. */
    public BigDecimal amount(String step, String side) {
        return sums.getOrDefault(key(step, LineSide.valueOf(side), "amount"), BigDecimal.ZERO);
    }

    /**
     * The value a {@code remember} VALUE step had in the latest earlier posted period of the tax year, or
     * null when no earlier period kept one.
     */
    public BigDecimal last(String step) {
        return sums.get(lastKey(step));
    }

    public static String lastKey(String step) {
        return "last:" + step;
    }

    public static String key(String step, LineSide side, String measure) {
        return step + "." + side + "." + measure;
    }
}
