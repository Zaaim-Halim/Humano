package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.Basis;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to replace the company-level payroll policy via {@code PUT /api/org-settings}.
 * Currency/calendar references are optional (null clears them).
 */
public record UpdateOrganizationSettingsRequest(
    @NotNull @DecimalMin("0.1") @DecimalMax("24") BigDecimal standardHoursPerDay,
    @NotNull @DecimalMin("1") @DecimalMax("168") BigDecimal standardHoursPerWeek,
    @NotNull @DecimalMin("1") @DecimalMax("744") BigDecimal standardMonthlyHours,
    @NotNull Basis defaultBasis,
    @NotNull @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal defaultOvertimeMultiplier,
    @NotNull @Size(min = 1, max = 60) String timezone,
    UUID defaultCurrencyId,
    UUID defaultPayrollCalendarId
) {}
