package com.humano.repository.payroll.projection;

import com.humano.domain.enumeration.payroll.PayrollLineCategory;
import java.math.BigDecimal;

/**
 * Year-to-date sums of one payroll template step for one employee, per line category: the bases
 * (stored in {@code PayrollLine.quantity}) and the amounts.
 */
public record TemplateYtdRow(String step, PayrollLineCategory category, BigDecimal base, BigDecimal amount) {}
