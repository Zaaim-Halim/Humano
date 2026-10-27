package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A competency in the performance framework, new or changed. {@code anchors} describe rating levels
 * 1 to 5 in order (blank levels allowed). Without {@code active} it stays as it is (new ones are
 * active).
 */
public record CompetencyRequest(
    @NotBlank(message = "Name is required") @Size(max = 100) String name,

    @Size(max = 1000) String description,

    @NotNull(message = "Category is required") CompetencyCategory category,

    @Size(max = 5) List<@Size(max = 500) String> anchors,

    Boolean active
) {}
