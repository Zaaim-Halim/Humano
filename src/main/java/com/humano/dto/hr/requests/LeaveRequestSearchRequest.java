package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching leave requests with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record LeaveRequestSearchRequest(
    UUID employeeId,
    UUID approverId,
    LeaveType leaveType,
    LeaveStatus status,
    LocalDate startDateFrom,
    LocalDate startDateTo,
    LocalDate endDateFrom,
    LocalDate endDateTo,
    String reason,
    Integer minDaysCount,
    Integer maxDaysCount,
    String createdBy,
    LocalDate createdDateFrom,
    LocalDate createdDateTo
) {}
