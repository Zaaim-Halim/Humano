package com.humano.dto.hr.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning a EmployeeEducation record.
 */
public record EmployeeEducationResponse(
    UUID id,
    UUID employeeId,
    String institution,
    String degree,
    String fieldOfStudy,
    LocalDate graduationDate,
    UUID documentFileId,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
