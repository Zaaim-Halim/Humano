package com.humano.dto.hr.requests;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for partially updating a EmploymentContract record. Null fields are left unchanged.
 */
public record UpdateEmploymentContractRequest(
    String contractNumber,
    LocalDate startDate,
    LocalDate endDate,
    String contractType,
    UUID positionId,
    UUID departmentId,
    BigDecimal workingHours,
    LocalDate signedDate,
    String status
) {}
