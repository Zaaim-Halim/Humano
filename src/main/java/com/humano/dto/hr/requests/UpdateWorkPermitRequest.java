package com.humano.dto.hr.requests;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for partially updating a WorkPermit record. Null fields are left unchanged.
 */
public record UpdateWorkPermitRequest(
    String visaType,
    String permitNumber,
    LocalDate issueDate,
    LocalDate expiryDate,
    String sponsor,
    UUID documentFileId
) {}
