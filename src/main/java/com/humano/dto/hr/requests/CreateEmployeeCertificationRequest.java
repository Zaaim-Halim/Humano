package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeCertification record.
 */
public record CreateEmployeeCertificationRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String name,
    String issuer,
    LocalDate issueDate,
    LocalDate expiryDate,
    Boolean verified,
    UUID documentFileId
) {}
