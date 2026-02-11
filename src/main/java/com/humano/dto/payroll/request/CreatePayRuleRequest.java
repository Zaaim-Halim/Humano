package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a pay rule.
 */
public record CreatePayRuleRequest(
    @NotNull(message = "Pay component ID is required") UUID payComponentId,

    @NotNull(message = "Formula is required") String formula,

    LocalDate effectiveFrom,

    LocalDate effectiveTo,

    Integer priority,

    @Size(max = 255, message = "Base formula reference cannot exceed 255 characters") String baseFormulaRef,

    boolean active
) {}
