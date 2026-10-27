package com.humano.dto.hr.workflow.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for the signed-in employee's self-assessment in a cycle. Narrative only: the rating
 * is the reviewer's.
 */
public record SelfAssessmentRequest(
    @NotBlank(message = "Achievements are required") @Size(max = 10000) String achievements,

    @Size(max = 10000) String challenges,

    @Size(max = 10000) String developmentGoals,

    @Size(max = 10000) String additionalComments,

    @Valid @Size(max = 20) List<@NotNull ReviewAnswerInput> answers
) {
    public SelfAssessmentRequest(String achievements, String challenges, String developmentGoals, String additionalComments) {
        this(achievements, challenges, developmentGoals, additionalComments, null);
    }
}
