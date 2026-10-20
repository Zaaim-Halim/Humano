package com.humano.dto.payroll.response;

import java.util.List;

/**
 * Result of enabling or configuring a payroll template: the template with its adoption, and anything
 * in the tenant's existing setup that interacts with it (ignored tax brackets, formula components that
 * may duplicate statutory amounts, employees without a profile, ...).
 */
public record PayrollTemplateAdoptionResponse(PayrollTemplateResponse template, List<String> warnings) {}
