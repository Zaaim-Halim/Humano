package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO record for processing (approving/rejecting) an overtime record.
 */
public record ProcessOvertimeRecordRequest(
    @NotNull(message = "Approval status is required") OvertimeApprovalStatus approvalStatus,

    @NotNull(message = "Approver ID is required") UUID approvedById,

    @Size(max = 500, message = "Notes must not exceed 500 characters") String notes
) {}
