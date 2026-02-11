package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for employee benefits summary.
 */
public record BenefitsSummaryResponse(
    UUID employeeId,
    String employeeName,
    int activeBenefitCount,
    BigDecimal totalEmployerCost,
    BigDecimal totalEmployeeCost,
    BigDecimal totalMonthlyCost,
    BigDecimal annualBenefitValue,
    Map<String, BigDecimal> costByBenefitType,
    List<EmployeeBenefitResponse> activeBenefits,
    List<EmployeeBenefitResponse> upcomingBenefits
) {}
