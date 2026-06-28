package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeSignature record.
 */
public record EmployeeSignatureResponse(
    UUID id,
    UUID employeeId,
    UUID signatureFileId,
    String certificate,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
