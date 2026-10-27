package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A draft proposal's decision: each part is optional — leaving them all empty records that pay stays as is.
 *
 * @param newSalary     the new base salary, on the current basis and currency
 * @param newPositionId the position promoted to
 * @param bonusAmount   a one-off performance bonus, in the pay currency
 * @param effectiveDate when the change takes effect
 * @param justification why, for the approver
 */
public record CompensationProposalRequest(
    @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 19, fraction = 2) BigDecimal newSalary,
    UUID newPositionId,
    @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 19, fraction = 2) BigDecimal bonusAmount,
    @NotNull LocalDate effectiveDate,
    @Size(max = 1000) String justification
) {}
