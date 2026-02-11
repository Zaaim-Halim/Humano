package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for payslip details.
 */
public record PayslipResponse(
    UUID id,
    String number,
    UUID employeeId,
    String employeeName,
    String employeeCode,
    String department,
    String position,
    UUID resultId,
    UUID periodId,
    String periodCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    BigDecimal gross,
    BigDecimal totalDeductions,
    BigDecimal net,
    String currencyCode,
    String pdfUrl,
    PayrollResultResponse details
) {}
