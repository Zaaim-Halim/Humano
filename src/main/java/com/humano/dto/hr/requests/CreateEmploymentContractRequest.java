package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a EmploymentContract record.
 */
public record CreateEmploymentContractRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
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
