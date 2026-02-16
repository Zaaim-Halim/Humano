package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for initiating a position/department transfer.
 */
public record InitiateTransferRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    UUID newDepartmentId,

    UUID newPositionId,

    UUID newManagerId,

    UUID newOrganizationalUnitId,

    @NotNull(message = "Effective date is required") LocalDate effectiveDate,

    @NotNull(message = "Transfer reason is required") String reason,

    String notes,

    boolean requiresRelocation
) {}
