package com.humano.dto.hr.workflow.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * An improvement plan, new or changed while it is a draft: what needs to improve, the objectives, the
 * support given, how often progress is reviewed, and the period.
 */
public record ImprovementPlanRequest(
    @NotBlank(message = "Say what needs to improve") @Size(max = 10000) String concerns,

    @Size(max = 10000) String support,

    @Size(max = 200) String meetingCadence,

    @NotNull(message = "Start date is required") LocalDate startDate,

    @NotNull(message = "End date is required") LocalDate endDate,

    @NotNull @Size(max = 10, message = "At most 10 objectives") List<@Valid Objective> objectives
) {
    /** What is expected, how it is measured, and optionally by when. */
    public record Objective(
        @NotBlank(message = "Describe the objective") @Size(max = 1000) String description,
        @Size(max = 1000) String successMeasure,
        LocalDate dueDate
    ) {}
}
