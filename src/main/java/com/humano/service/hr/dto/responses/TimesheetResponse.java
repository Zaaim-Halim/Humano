package com.humano.service.hr.dto.responses;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning timesheet information.
 */
public record TimesheetResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    LocalDate date,
    BigDecimal hoursWorked,
    UUID projectId,
    String projectName,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
