package com.humano.service.hr.dto.responses;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning leave request information.
 */
public record LeaveRequestResponse(
    UUID id,
    LocalDate startDate,
    LocalDate endDate,
    LeaveType leaveType,
    LeaveStatus status,
    String reason,
    Integer daysCount,
    UUID employeeId,
    String employeeName,
    UUID approverId,
    String approverName,
    String approverComments,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
