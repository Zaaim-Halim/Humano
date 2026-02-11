package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for approving a payroll run.
 */
public record ApprovePayrollRunRequest(
    @NotNull(message = "Payroll run ID is required") UUID payrollRunId,

    @NotNull(message = "Approver ID is required") UUID approverId,

    String approvalNotes,

    boolean forceApproval
) {}
