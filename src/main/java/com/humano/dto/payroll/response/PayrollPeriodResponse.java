package com.humano.dto.payroll.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for payroll period details.
 */
public record PayrollPeriodResponse(
    UUID id,
    String code,
    UUID calendarId,
    String calendarName,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate paymentDate,
    boolean closed,
    int payrollRunCount,
    boolean hasApprovedRun
) {}
