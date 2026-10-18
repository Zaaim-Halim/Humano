package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.OnboardingTaskOwner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO replacing the company onboarding policy: the default length and
 * the checklist, in order. Each task's {@code dueDay} must fall within
 * {@code defaultDays} (checked by the service).
 */
public record UpdateOnboardingChecklistRequest(
    @Min(value = 1, message = "Default length must be at least 1 day")
    @Max(value = 365, message = "Default length must be at most 365 days")
    int defaultDays,

    @NotEmpty(message = "The checklist needs at least one task") @Size(max = 50) List<@Valid @NotNull Task> tasks
) {
    public record Task(
        @NotBlank(message = "Task title is required") @Size(max = 255) String title,
        @Size(max = 1000) String description,
        @Min(value = 0, message = "Due day can't be negative") int dueDay,
        @NotNull(message = "Task owner is required") OnboardingTaskOwner owner
    ) {}
}
