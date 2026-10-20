package com.humano.service.payroll.template.engine;

import com.humano.service.errors.BusinessRuleViolationException;

/**
 * A template could not compute an employee's statutory amounts exactly — missing input, unsupported
 * case, data not covering the period. Surfaces as a per-employee calculation error in the run.
 */
public class TemplateCalculationException extends BusinessRuleViolationException {

    private static final long serialVersionUID = 1L;

    public TemplateCalculationException(String message) {
        super(message);
    }
}
