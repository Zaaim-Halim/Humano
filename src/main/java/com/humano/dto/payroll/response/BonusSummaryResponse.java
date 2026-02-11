package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for bonus summary statistics.
 */
public record BonusSummaryResponse(
    UUID employeeId,
    String employeeName,
    int totalBonusCount,
    BigDecimal totalBonusAmount,
    BigDecimal paidAmount,
    BigDecimal pendingAmount,
    Map<String, BigDecimal> bonusByType,
    List<BonusResponse> recentBonuses,
    int yearToDateCount,
    BigDecimal yearToDateAmount
) {}
