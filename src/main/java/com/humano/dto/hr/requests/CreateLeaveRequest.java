package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.LeaveType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for creating a new leave request.
 */
public record CreateLeaveRequest(
    @NotNull(message = "Start date is required") LocalDate startDate,

    @NotNull(message = "End date is required") LocalDate endDate,

    @NotNull(message = "Leave type is required") LeaveType leaveType,

    @NotNull(message = "Reason is required")
    @Size(min = 20, max = 1000, message = "Reason must be between 20 and 1000 characters")
    String reason,

    @NotNull(message = "Employee ID is required") UUID employeeId
) {}
