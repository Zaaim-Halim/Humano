package com.humano.dto.hr.requests;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for partially updating a EmployeeLicense record. Null fields are left unchanged.
 */
public record UpdateEmployeeLicenseRequest(
    String name,
    String issuer,
    LocalDate issueDate,
    LocalDate expiryDate,
    Boolean verified,
    UUID documentFileId
) {}
