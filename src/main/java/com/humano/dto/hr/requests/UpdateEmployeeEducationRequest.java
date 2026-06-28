package com.humano.dto.hr.requests;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for partially updating a EmployeeEducation record. Null fields are left unchanged.
 */
public record UpdateEmployeeEducationRequest(
    String institution,
    String degree,
    String fieldOfStudy,
    LocalDate graduationDate,
    UUID documentFileId
) {}
