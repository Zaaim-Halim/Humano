package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for initiating employee onboarding.
 */
public record InitiateOnboardingRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    UUID departmentId,

    UUID positionId,

    UUID managerId,

    UUID organizationalUnitId,

    LocalDate startDate,

    String employeeNumber,

    String workEmail,

    String workPhone,

    List<UUID> requiredTrainingIds,

    List<UUID> benefitIds,

    Map<String, Object> additionalContext,

    String notes
) {}
