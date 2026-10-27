package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.FeedbackVisibility;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * A new review cycle, or the changes to one still in draft. Without departments (or with
 * {@code includeAllDepartments}) the cycle covers every working employee. The additional feedback
 * settings default to: visible to the reviewer only, nominations allowed, 8 contributors at most
 * (0 turns additional feedback off). Second-level sign-off of written reviews is off unless
 * {@code requiresSignOff}. The type defaults to REGULAR; an AD_HOC cycle reviews the listed
 * {@code employeeIds} (and ignores departments), the other types ignore them. Without
 * {@code templateId} reviews use the standard form only.
 */
public record InitiateReviewCycleRequest(
    @NotBlank(message = "Cycle name is required") @Size(max = 100) String name,

    @Size(max = 500) String description,

    @NotNull(message = "Review period start date is required") LocalDate reviewPeriodStart,

    @NotNull(message = "Review period end date is required") LocalDate reviewPeriodEnd,

    @NotNull(message = "Cycle start date is required") LocalDate startDate,

    @NotNull(message = "Cycle end date is required") LocalDate endDate,

    LocalDate selfAssessmentDeadline,

    LocalDate managerReviewDeadline,

    LocalDate calibrationDeadline,

    LocalDate feedbackDeadline,

    Set<UUID> departmentIds,

    boolean includeAllDepartments,

    FeedbackVisibility feedbackVisibility,

    Boolean allowFeedbackNominations,

    @Min(0) @Max(20) Integer maxContributors,

    Boolean requiresSignOff,

    ReviewCycleType type,

    Set<UUID> employeeIds,

    UUID templateId
) {}
