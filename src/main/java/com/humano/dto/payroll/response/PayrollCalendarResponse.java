package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.payroll.Frequency;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for payroll calendar details.
 */
public record PayrollCalendarResponse(
    UUID id,
    String name,
    Frequency frequency,
    String timezone,
    boolean active,
    int periodCount,
    List<PayrollPeriodSummary> upcomingPeriods
) {
    public record PayrollPeriodSummary(
        UUID id,
        String code,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate paymentDate,
        boolean closed
    ) {}
}
