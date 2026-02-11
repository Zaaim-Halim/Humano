package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for salary history with trend analysis.
 */
public record SalaryHistoryResponse(
    UUID employeeId,
    String employeeName,
    List<SalaryChange> history,
    BigDecimal totalGrowthPercentage,
    BigDecimal averageAnnualGrowth,
    CompensationResponse currentCompensation
) {
    public record SalaryChange(
        UUID compensationId,
        BigDecimal previousAmount,
        BigDecimal newAmount,
        Basis basis,
        String currencyCode,
        BigDecimal changeAmount,
        BigDecimal changePercentage,
        LocalDate effectiveFrom,
        String reason,
        String changedBy
    ) {}
}
