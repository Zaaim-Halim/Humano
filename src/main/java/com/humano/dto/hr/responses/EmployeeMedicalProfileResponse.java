package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeMedicalProfile record.
 */
public record EmployeeMedicalProfileResponse(
    UUID id,
    UUID employeeId,
    String bloodType,
    String allergies,
    String emergencyNotes,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
