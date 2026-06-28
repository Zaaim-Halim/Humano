package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning a WorkPermit record.
 */
public record WorkPermitResponse(
    UUID id,
    UUID employeeId,
    String visaType,
    String permitNumber,
    LocalDate issueDate,
    LocalDate expiryDate,
    String sponsor,
    UUID documentFileId,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
