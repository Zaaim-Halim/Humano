package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for payroll input details.
 */
public record PayrollInputResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    UUID periodId,
    String periodCode,
    UUID componentId,
    String componentCode,
    String componentName,
    BigDecimal quantity,
    BigDecimal rate,
    BigDecimal amount,
    BigDecimal calculatedAmount,
    String source,
    String metadata
) {}
