package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for individual payroll result (employee payslip data).
 */
public record PayrollResultResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    String employeeCode,
    UUID runId,
    UUID periodId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    BigDecimal gross,
    BigDecimal totalDeductions,
    BigDecimal net,
    BigDecimal employerCost,
    String currencyCode,
    List<PayrollLineItem> earnings,
    List<PayrollLineItem> deductions,
    List<PayrollLineItem> employerCharges,
    String payslipNumber,
    String payslipUrl
) {
    public record PayrollLineItem(
        UUID id,
        String componentCode,
        String componentName,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount,
        int sequence,
        String explanation
    ) {}
}
