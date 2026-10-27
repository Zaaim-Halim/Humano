package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ImprovementPlanProgress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A progress review in an improvement plan: where things stand and what was discussed.
 */
public record ImprovementPlanCheckInRequest(
    @NotNull(message = "Say how it is going") ImprovementPlanProgress progress,

    @NotBlank(message = "Record what was discussed") @Size(max = 10000) String note
) {}
