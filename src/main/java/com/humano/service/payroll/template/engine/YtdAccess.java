package com.humano.service.payroll.template.engine;

import com.humano.service.payroll.template.model.LineSide;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Bound as {@code #ytd} while a template runs: what this template's steps produced for the employee
 * earlier in the tax year, from posted payroll runs only (the current run is excluded). A step with
 * no earlier lines sums to zero.
 */
public final class YtdAccess {

    private final Map<String, BigDecimal> sums;

    /** @param sums keyed {@code step.SIDE.base} / {@code step.SIDE.amount} */
    YtdAccess(Map<String, BigDecimal> sums) {
        this.sums = sums == null ? Map.of() : sums;
    }

    /** Sum of the bases of a step's lines on {@code side} (EMPLOYEE, EMPLOYER or TAX) — e.g. wages already subject to a capped contribution. */
    public BigDecimal base(String step, String side) {
        return sums.getOrDefault(key(step, LineSide.valueOf(side), "base"), BigDecimal.ZERO);
    }

    /** Sum of the amounts of a step's lines on {@code side}. */
    public BigDecimal amount(String step, String side) {
        return sums.getOrDefault(key(step, LineSide.valueOf(side), "amount"), BigDecimal.ZERO);
    }

    public static String key(String step, LineSide side, String measure) {
        return step + "." + side + "." + measure;
    }
}
