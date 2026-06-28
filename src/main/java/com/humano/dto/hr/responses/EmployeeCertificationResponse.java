package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeCertification record.
 */
public record EmployeeCertificationResponse(
    UUID id,
    UUID employeeId,
    String name,
    String issuer,
    LocalDate issueDate,
    LocalDate expiryDate,
    Boolean verified,
    UUID documentFileId,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
