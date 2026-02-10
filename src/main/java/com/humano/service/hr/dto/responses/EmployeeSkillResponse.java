package com.humano.service.hr.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning employee skill information.
 */
public record EmployeeSkillResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    UUID skillId,
    String skillName,
    String skillCategory,
    Integer proficiencyLevel,
    LocalDate acquisitionDate,
    LocalDate expiryDate,
    String notes,
    Boolean isVerified,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
