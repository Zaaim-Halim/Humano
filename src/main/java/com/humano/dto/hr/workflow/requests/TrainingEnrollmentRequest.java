package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TrainingEnrollmentRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Training ID is required") UUID trainingId,

    String justification,

    boolean managerApprovalRequired,

    UUID requestedById,

    String notes
) {}
