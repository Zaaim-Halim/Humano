package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.PositionChangeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A promotion, demotion, transfer or other change of someone's position, department and/or pay,
 * from one date. Left-out fields stay as they are; at least one thing must change.
 *
 * @param newSalary the new base amount, on the current pay basis and currency
 */
public record PositionChangeRequest(
    @NotNull PositionChangeType changeType,
    @NotNull LocalDate effectiveDate,
    UUID newPositionId,
    UUID newDepartmentId,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal newSalary,
    @NotBlank @Size(max = 255) String reason
) {}
