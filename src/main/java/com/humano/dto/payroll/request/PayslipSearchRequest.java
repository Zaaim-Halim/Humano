package com.humano.dto.payroll.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching payslips with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record PayslipSearchRequest(
    UUID employeeId,
    String payslipNumber,
    UUID payrollRunId,
    BigDecimal minGross,
    BigDecimal maxGross,
    BigDecimal minNet,
    BigDecimal maxNet,
    LocalDate periodStartFrom,
    LocalDate periodStartTo,
    LocalDate periodEndFrom,
    LocalDate periodEndTo,
    String createdBy,
    Instant createdDateFrom,
    Instant createdDateTo
) {}
