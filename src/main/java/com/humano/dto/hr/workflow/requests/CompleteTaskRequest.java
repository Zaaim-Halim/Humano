package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for completing a workflow task.
 */
public record CompleteTaskRequest(
    @NotNull(message = "Task ID is required") UUID taskId,

    String completionNotes,

    boolean successful,

    String outcome
) {}
