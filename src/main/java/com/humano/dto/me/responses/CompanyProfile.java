package com.humano.dto.me.responses;

import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The company's settings that every screen may read (Settings → Company / Payroll & time):
 * identity, timezone, currency and working-time policy. Nothing sensitive.
 */
public record CompanyProfile(
    String name,
    String logo,
    String timezone,
    UUID defaultCurrencyId,
    String currencyCode,
    BigDecimal standardHoursPerDay,
    BigDecimal standardHoursPerWeek,
    BigDecimal standardMonthlyHours,
    BigDecimal overtimeMultiplier,
    Basis defaultBasis
) {}
