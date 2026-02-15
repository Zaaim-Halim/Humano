package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.BonusType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching bonuses with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record BonusSearchRequest(
    UUID employeeId,
    BonusType type,
    UUID currencyId,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    LocalDate awardDateFrom,
    LocalDate awardDateTo,
    LocalDate paymentDateFrom,
    LocalDate paymentDateTo,
    Boolean isPaid,
    Boolean isTaxable,
    String description,
    String createdBy,
    Instant createdDateFrom,
    Instant createdDateTo
) {}
