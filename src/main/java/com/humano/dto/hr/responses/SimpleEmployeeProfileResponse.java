package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.util.UUID;

/**
 * A simplified version of EmployeeProfileResponse for list views.
 * Contains only essential information about an employee for display in lists.
 */
public record SimpleEmployeeProfileResponse(
    UUID id,
    String jobTitle,
    String phone,
    EmployeeStatus status,
    String departmentName,
    String positionName
) {}
