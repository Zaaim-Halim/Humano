package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO record for updating an existing health insurance record.
 */
public record UpdateHealthInsuranceRequest(
    String providerName,

    String policyNumber,

    LocalDate startDate,

    LocalDate endDate,

    @DecimalMin(value = "0.0", message = "Coverage amount must be non-negative") BigDecimal coverageAmount,

    HealthInsuranceStatus status
) {}
