package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for initiating employee onboarding. Department, position, manager
 * and contact details are not set here — they belong on the employee profile.
 */
public record InitiateOnboardingRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    LocalDate startDate,

    /** Day the onboarding should be finished by; defaults to the company's default length after the start. Task due dates are spread across this window. */
    LocalDate targetDate,

    /** Trainings to enroll the new hire in (planned); adds a "Complete Mandatory Trainings" task. Requires the Training module. */
    List<UUID> requiredTrainingIds,

    Map<String, Object> additionalContext,

    String notes
) {}
