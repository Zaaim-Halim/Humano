package com.humano.service.payroll.template.extension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * A code building block for rules a template can't declare — typically an algorithm the authority
 * publishes as a program (e.g. Germany's Lohnsteuer PAP). Templates call it from an EXTENSION step
 * by {@link #id()} and use its outputs in later steps; lines are still emitted by the template.
 * <p>
 * Implementations are Spring beans and must be pure: same inputs, same outputs; no database,
 * clock, network or randomness. The id names the rule version (e.g. {@code de.lohnsteuer-pap-2026})
 * because published algorithms change yearly — a new year is a new extension, so templates for past
 * years keep computing exactly as before.
 */
public interface PayrollTemplateExtension {
    /** Stable id templates reference, including the rule version. */
    String id();

    /** Input names the extension reads; a template must supply exactly these. */
    Set<String> inputNames();

    /** Output names the extension always returns. */
    Set<String> outputNames();

    /** Computes the outputs. Inputs are BigDecimal, Integer, Boolean or String values. */
    Map<String, BigDecimal> compute(Map<String, Object> inputs);
}
