package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A progress update on a goal: how far along it is (0–100) and an optional note.
 */
public record GoalCheckInRequest(
    @NotNull(message = "Progress is required") @Min(0) @Max(100) Integer progress,
    @Size(max = 2000) String comment
) {}
