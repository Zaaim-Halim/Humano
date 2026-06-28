package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a EmergencyContact record.
 */
public record CreateEmergencyContactRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String name,
    String relationship,
    String phone,
    String email
) {}
