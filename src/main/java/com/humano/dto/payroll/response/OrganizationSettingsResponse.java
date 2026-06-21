package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response for {@code GET/PUT /api/org-settings} — the company-level payroll
 * policy. {@code id} is {@code null} when no settings row has been saved yet
 * (the read returns transient defaults).
 */
public record OrganizationSettingsResponse(
    UUID id,
    BigDecimal standardHoursPerDay,
    BigDecimal standardHoursPerWeek,
    BigDecimal standardMonthlyHours,
    Basis defaultBasis,
    BigDecimal defaultOvertimeMultiplier,
    String timezone,
    UUID defaultCurrencyId,
    String defaultCurrencyCode,
    UUID defaultPayrollCalendarId,
    String defaultPayrollCalendarName
) {}
