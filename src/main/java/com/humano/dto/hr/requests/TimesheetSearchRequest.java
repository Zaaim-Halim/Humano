package com.humano.dto.hr.requests;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching timesheets with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record TimesheetSearchRequest(
    UUID employeeId,
    UUID projectId,
    LocalDate dateFrom,
    LocalDate dateTo,
    BigDecimal minHours,
    BigDecimal maxHours,
    String createdBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
