package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a EmployeeEducation record.
 */
public record CreateEmployeeEducationRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String institution,
    String degree,
    String fieldOfStudy,
    LocalDate graduationDate,
    UUID documentFileId
) {}
