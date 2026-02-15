package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.DeductionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching deductions with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record DeductionSearchRequest(
    UUID employeeId,
    DeductionType type,
    UUID currencyId,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    BigDecimal minPercentage,
    BigDecimal maxPercentage,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean isPreTax,
    Boolean activeOnly,
    String description,
    String createdBy,
    Instant createdDateFrom,
    Instant createdDateTo
) {}
