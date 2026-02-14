package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching employees with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record EmployeeSearchRequest(
    String firstName,
    String lastName,
    String email,
    String jobTitle,
    String phone,
    EmployeeStatus status,
    UUID departmentId,
    UUID positionId,
    UUID unitId,
    UUID managerId,
    LocalDate startDateFrom,
    LocalDate startDateTo,
    LocalDate endDateFrom,
    LocalDate endDateTo
) {}
