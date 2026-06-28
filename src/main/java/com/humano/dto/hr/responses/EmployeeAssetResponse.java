package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeAsset record.
 */
public record EmployeeAssetResponse(
    UUID id,
    UUID employeeId,
    String type,
    String identifier,
    LocalDate assignedDate,
    LocalDate returnedDate,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
