package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.LeaveStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for processing (approving/rejecting) a leave request.
 */
public record ProcessLeaveRequest(
    @NotNull(message = "Status is required") LeaveStatus status,

    @Size(max = 500, message = "Comments must not exceed 500 characters") String approverComments,

    @NotNull(message = "Approver ID is required") UUID approverId
) {}
