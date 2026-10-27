package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * More time for an improvement plan: the new end date and why.
 */
public record ExtendImprovementPlanRequest(
    @NotNull(message = "The new end date is required") LocalDate endDate,

    @NotBlank(message = "Say why the plan is extended") @Size(max = 500) String reason
) {}
