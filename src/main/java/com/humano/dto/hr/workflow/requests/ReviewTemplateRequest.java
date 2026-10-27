package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * A review template, new or changed: the competencies the reviewer rates and the questions asked,
 * each in order. Without {@code active} it stays as it is (new ones are active).
 */
public record ReviewTemplateRequest(
    @NotBlank(message = "Name is required") @Size(max = 100) String name,

    @Size(max = 1000) String description,

    @Size(max = 20) List<@NotNull UUID> competencyIds,

    @Size(max = 20) List<@Valid @NotNull Question> questions,

    Boolean active
) {
    public record Question(
        @NotNull(message = "Say who answers the question") ReviewQuestionAudience audience,
        @NotBlank(message = "The question is required") @Size(max = 500) String text,
        @Size(max = 1000) String helpText,
        boolean required
    ) {}
}
