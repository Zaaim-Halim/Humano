package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Enables or disables a payroll template for the tenant and sets its employer settings (validated
 * against the template's declared inputs).
 */
public record ConfigurePayrollTemplateRequest(@NotNull(message = "enabled is required") Boolean enabled, Map<String, String> settings) {}
