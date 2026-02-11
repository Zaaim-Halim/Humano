package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.DeductionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for deduction details.
 */
public record DeductionResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    DeductionType type,
    BigDecimal amount,
    BigDecimal percentage,
    String currencyCode,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean active,
    String description
) {}
