package com.humano.service.payroll.template.engine;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure functions template expressions call as {@code #name(...)}. Unlike the tenant formula
 * functions, these are strict: a null argument is a template bug or a missing input, so it throws
 * instead of counting as zero.
 */
public final class TemplateFunctions {

    private TemplateFunctions() {}

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return req(a, "min").min(req(b, "min"));
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return req(a, "max").max(req(b, "max"));
    }

    /** {@code value} limited to [{@code lo}, {@code hi}]. */
    public static BigDecimal clamp(BigDecimal value, BigDecimal lo, BigDecimal hi) {
        return req(value, "clamp").max(req(lo, "clamp")).min(req(hi, "clamp"));
    }

    /** {@code max(0, value)}. */
    public static BigDecimal pos(BigDecimal value) {
        return req(value, "pos").max(BigDecimal.ZERO);
    }

    /** {@code value} rounded to {@code scale} decimals with {@code mode} (HALF_UP, HALF_EVEN, DOWN, UP, FLOOR, CEILING). */
    public static BigDecimal round(BigDecimal value, int scale, String mode) {
        return req(value, "round").setScale(scale, RoundingMode.valueOf(mode));
    }

    /** {@code a / b} to {@code scale} decimals with {@code mode}. Division is only available through this function. */
    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale, String mode) {
        BigDecimal divisor = req(b, "divide");
        if (divisor.signum() == 0) {
            throw new IllegalArgumentException("#divide by zero");
        }
        return req(a, "divide").divide(divisor, scale, RoundingMode.valueOf(mode));
    }

    /** {@code value} when it is not null, otherwise {@code fallback}. */
    public static BigDecimal coalesce(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : req(fallback, "coalesce");
    }

    private static BigDecimal req(BigDecimal value, String fn) {
        if (value == null) {
            throw new IllegalArgumentException("#" + fn + " received a missing value");
        }
        return value;
    }

    static Map<String, Method> registry() {
        Map<String, Method> functions = new LinkedHashMap<>();
        for (Method m : TemplateFunctions.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                if (functions.put(m.getName(), m) != null) {
                    throw new IllegalStateException("Template functions can't be overloaded: " + m.getName());
                }
            }
        }
        return Map.copyOf(functions);
    }
}
