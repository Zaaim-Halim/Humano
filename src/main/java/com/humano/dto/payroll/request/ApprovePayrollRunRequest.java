package com.humano.dto.payroll.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request DTO for approving a payroll run. A run whose latest calculation could not pay some
 * employees is approved only with {@code approveDespiteIssues} and an {@code issueOverrideReason},
 * which is recorded with the run; those employees are not paid by it.
 */
public record ApprovePayrollRunRequest(
    @NotNull(message = "Payroll run ID is required") UUID payrollRunId,

    @NotNull(message = "Approver ID is required") UUID approverId,

    String approvalNotes,

    boolean forceApproval,

    boolean approveDespiteIssues,

    @Size(max = 1000, message = "Reason must not exceed 1000 characters") String issueOverrideReason
) {}
