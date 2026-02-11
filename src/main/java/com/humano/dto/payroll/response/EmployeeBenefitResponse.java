package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.BenefitStatus;
import com.humano.domain.enumeration.payroll.BenefitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for employee benefit details.
 */
public record EmployeeBenefitResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    BenefitType type,
    BigDecimal employerCost,
    BigDecimal employeeCost,
    BigDecimal totalCost,
    String currencyCode,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    BenefitStatus status,
    String planName,
    String coverageLevel
) {}
