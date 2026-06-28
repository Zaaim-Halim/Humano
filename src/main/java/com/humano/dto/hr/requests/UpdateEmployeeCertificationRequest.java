package com.humano.dto.hr.requests;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for partially updating a EmployeeCertification record. Null fields are left unchanged.
 */
public record UpdateEmployeeCertificationRequest(
    String name,
    String issuer,
    LocalDate issueDate,
    LocalDate expiryDate,
    Boolean verified,
    UUID documentFileId
) {}
