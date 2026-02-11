package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.BenefitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for enrolling an employee in a benefit.
 */
public record EnrollBenefitRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Benefit type is required") BenefitType type,

    @DecimalMin(value = "0.0", message = "Employer cost cannot be negative") BigDecimal employerCost,

    @DecimalMin(value = "0.0", message = "Employee cost cannot be negative") BigDecimal employeeCost,

    UUID currencyId,

    @NotNull(message = "Effective from date is required") LocalDate effectiveFrom,

    LocalDate effectiveTo,

    String planName,

    String coverageLevel,

    String notes
) {}
