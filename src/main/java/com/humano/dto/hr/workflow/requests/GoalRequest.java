package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal, new or changed. Without {@code employeeId} it is the signed-in employee's own goal (ignored
 * on a change). {@code weight} is the goal's share of the employee's goals in percent, optional.
 */
public record GoalRequest(
    UUID employeeId,

    @NotBlank(message = "Title is required") @Size(max = 200) String title,

    @Size(max = 2000) String description,

    @Size(max = 1000) String successMeasure,

    @Min(0) @Max(100) Integer weight,

    @NotNull(message = "Start date is required") LocalDate startDate,

    @NotNull(message = "Due date is required") LocalDate dueDate
) {}
