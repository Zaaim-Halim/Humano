package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.RunStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for payroll run details.
 */
public record PayrollRunResponse(
    UUID id,
    UUID periodId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    String scope,
    RunStatus status,
    int employeeCount,
    BigDecimal totalGross,
    BigDecimal totalDeductions,
    BigDecimal totalNet,
    BigDecimal totalEmployerCost,
    String currencyCode,
    OffsetDateTime approvedAt,
    String approvedBy,
    List<PayrollValidationError> validationErrors,
    OffsetDateTime createdAt,
    String createdBy
) {
    public record PayrollValidationError(UUID employeeId, String employeeName, String errorCode, String message, String severity) {}
}
