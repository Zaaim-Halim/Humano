package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching employees with multiple criteria.
 * All field-specific criteria are optional and combined with AND logic. {@code query}
 * is a single free-text term matched (OR) against first name, last name, and job title —
 * for single-box pickers/typeaheads.
 */
public record EmployeeSearchRequest(
    String query,
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
