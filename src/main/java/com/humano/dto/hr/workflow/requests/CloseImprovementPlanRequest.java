package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ImprovementPlanOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * How an improvement plan ended, with the reasoning. {@code CANCELLED} stops it early.
 */
public record CloseImprovementPlanRequest(
    @NotNull(message = "Choose the outcome") ImprovementPlanOutcome outcome,

    @NotBlank(message = "Explain the outcome") @Size(max = 10000) String note
) {}
