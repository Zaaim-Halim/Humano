package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for initiating an employee offboarding workflow.
 */
public record InitiateOffboardingRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Last working date is required") LocalDate lastWorkingDate,

    @NotNull(message = "Reason is required") String reason,

    String offboardingType, // RESIGNATION, TERMINATION, RETIREMENT, etc.

    /** Termination reason from the company's list (Settings → Lists); recorded on the employee when they leave. */
    UUID terminationReasonId,

    boolean conductExitInterview,

    UUID exitInterviewerId,

    String notes,

    Map<String, Object> additionalContext
) {}
