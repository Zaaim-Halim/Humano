package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning employee document information.
 * Contains all document details including audit information.
 */
public record EmployeeDocumentResponse(
    UUID id,
    String type,
    String filePath,
    UUID employeeId,
    String employeeName,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
