package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeMedicalProfile record.
 */
public record CreateEmployeeMedicalProfileRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String bloodType,
    String allergies,
    String emergencyNotes
) {}
