package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * DTO record for updating an employee profile.
 * All fields are optional to allow partial updates.
 * <p>
 * When {@link #authorities} is non-null it is a full replacement of the
 * employee's granted roles (the {@code EMPLOYEE} role is always preserved);
 * leave it null to keep the existing roles untouched.
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
    UUID managerId,
    Set<String> authorities
) {}
