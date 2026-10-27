package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The reviewer's rating of one of a cycle's competencies, with an optional comment.
 */
public record CompetencyRatingInput(
    @NotNull UUID competencyId,

    @NotNull(message = "Rate every competency") @Min(1) @Max(5) Integer rating,

    @Size(max = 2000) String comment
) {}
