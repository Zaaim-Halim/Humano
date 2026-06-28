package com.humano.dto.hr.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning a EmploymentContract record.
 */
public record EmploymentContractResponse(
    UUID id,
    UUID employeeId,
    String contractNumber,
    LocalDate startDate,
    LocalDate endDate,
    String contractType,
    UUID positionId,
    UUID departmentId,
    BigDecimal workingHours,
    LocalDate signedDate,
    String status,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
