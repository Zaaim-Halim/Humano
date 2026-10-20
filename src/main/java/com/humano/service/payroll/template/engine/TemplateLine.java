package com.humano.service.payroll.template.engine;

import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.service.payroll.template.model.LineSide;
import java.math.BigDecimal;

/**
 * An amount a template computed.
 *
 * @param templateRef {@code id@version} of the template
 * @param step        the step that produced it
 * @param side        employee contribution, employer contribution or tax
 * @param label       payslip label
 * @param ledger      tax type for the payroll line
 * @param base        the amount the rule was applied to (null when the step has none)
 * @param rate        the flat rate applied, as a fraction (null for non-flat amounts)
 * @param amount      the rounded amount
 * @param explain     how it was derived, with its source
 */
public record TemplateLine(
    String templateRef,
    String step,
    LineSide side,
    String label,
    TaxType ledger,
    BigDecimal base,
    BigDecimal rate,
    BigDecimal amount,
    String explain
) {}
