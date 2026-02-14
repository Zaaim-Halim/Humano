package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new employee profile.
 * Contains all necessary fields for creating a basic employee profile.
 */
public record CreateEmployeeProfileRequest(
    String jobTitle,
    String phone,
    @NotNull(message = "Start date is required") LocalDate startDate,
    LocalDate endDate,
    EmployeeStatus status,
    UUID countryId,
    UUID departmentId,
    @NotNull(message = "Position is required") UUID positionId,
    @NotNull(message = "Organizational unit is required") UUID unitId,
    UUID managerId
) {}
