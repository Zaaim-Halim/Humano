package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for compensation details.
 */
public record CompensationResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    UUID currencyId,
    String currencyCode,
    BigDecimal baseAmount,
    Basis basis,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean active
) {}
