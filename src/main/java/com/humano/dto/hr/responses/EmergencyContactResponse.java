package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a EmergencyContact record.
 */
public record EmergencyContactResponse(
    UUID id,
    UUID employeeId,
    String name,
    String relationship,
    String phone,
    String email,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
