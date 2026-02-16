package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for initiating a performance review cycle.
 */
public record InitiateReviewCycleRequest(
    @NotNull(message = "Cycle name is required") String name,

    String description,

    @NotNull(message = "Review period start date is required") LocalDate reviewPeriodStart,

    @NotNull(message = "Review period end date is required") LocalDate reviewPeriodEnd,

    @NotNull(message = "Cycle start date is required") LocalDate startDate,

    @NotNull(message = "Cycle end date is required") LocalDate endDate,

    LocalDate selfAssessmentDeadline,

    LocalDate managerReviewDeadline,

    LocalDate calibrationDeadline,

    LocalDate feedbackDeadline,

    Set<UUID> departmentIds,

    boolean includeAllDepartments
) {}
