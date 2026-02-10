package com.humano.service.hr.dto.responses;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning health insurance information.
 */
public record HealthInsuranceResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String providerName,
    String policyNumber,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal coverageAmount,
    HealthInsuranceStatus status,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
