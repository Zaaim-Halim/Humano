package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One change in someone's career: what moved, from what to what, from when and why. A "to" side is
 * null when that part did not change. Salaries are null unless the change set one and the reader
 * may see pay.
 */
public record PositionChangeResponse(
    UUID id,
    PositionChangeType changeType,
    PositionChangeStatus status,
    LocalDate effectiveDate,
    String reason,
    Ref oldPosition,
    Ref newPosition,
    Ref oldDepartment,
    Ref newDepartment,
    Ref oldUnit,
    Ref newUnit,
    Ref oldManager,
    Ref newManager,
    BigDecimal oldSalary,
    BigDecimal newSalary,
    String recordedBy,
    Instant recordedAt
) {
    public record Ref(UUID id, String name) {}
}
