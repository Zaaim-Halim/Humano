package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching compensations with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record CompensationSearchRequest(
    UUID employeeId,
    UUID positionId,
    UUID currencyId,
    Basis basis,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Boolean activeOnly,
    String createdBy,
    Instant createdDateFrom,
    Instant createdDateTo
) {}
