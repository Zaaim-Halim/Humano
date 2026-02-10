package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new health insurance record.
 */
public record CreateHealthInsuranceRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotBlank(message = "Provider name is required") String providerName,

    @NotBlank(message = "Policy number is required") String policyNumber,

    @NotNull(message = "Start date is required") LocalDate startDate,

    @NotNull(message = "End date is required") LocalDate endDate,

    @NotNull(message = "Coverage amount is required")
    @DecimalMin(value = "0.0", message = "Coverage amount must be non-negative")
    BigDecimal coverageAmount,

    @NotNull(message = "Status is required") HealthInsuranceStatus status
) {}
