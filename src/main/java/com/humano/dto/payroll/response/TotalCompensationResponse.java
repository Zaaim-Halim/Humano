package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for total compensation statement.
 */
public record TotalCompensationResponse(
    UUID employeeId,
    String employeeName,
    String positionTitle,
    String department,
    int year,
    BigDecimal baseSalary,
    BigDecimal totalBonuses,
    BigDecimal totalBenefitsValue,
    BigDecimal totalCompensation,
    String currencyCode,
    CompensationBreakdown breakdown,
    List<MonthlyCompensation> monthlyDetails,
    YearOverYearComparison yearComparison
) {
    public record CompensationBreakdown(
        BigDecimal baseSalaryPercentage,
        BigDecimal bonusPercentage,
        BigDecimal benefitsPercentage,
        Map<String, BigDecimal> bonusByType,
        Map<String, BigDecimal> benefitsByType
    ) {}

    public record MonthlyCompensation(
        int month,
        String monthName,
        BigDecimal gross,
        BigDecimal net,
        BigDecimal bonuses,
        BigDecimal deductions
    ) {}

    public record YearOverYearComparison(
        BigDecimal previousYearTotal,
        BigDecimal currentYearTotal,
        BigDecimal changeAmount,
        BigDecimal changePercentage
    ) {}
}
