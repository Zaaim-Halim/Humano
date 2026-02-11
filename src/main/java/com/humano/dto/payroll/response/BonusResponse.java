package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.BonusType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for bonus details.
 */
public record BonusResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    BonusType type,
    BigDecimal amount,
    String currencyCode,
    LocalDate awardDate,
    LocalDate paymentDate,
    boolean paid,
    String description
) {}
