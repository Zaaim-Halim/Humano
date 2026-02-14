package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for updating an employee profile.
 * All fields are optional to allow partial updates.
 */
public record UpdateEmployeeProfileRequest(
    String jobTitle,
    String phone,
    LocalDate startDate,
    LocalDate endDate,
    EmployeeStatus status,
    UUID countryId,
    UUID departmentId,
    UUID positionId,
    UUID unitId,
    UUID managerId
) {}
