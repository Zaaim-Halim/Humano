package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ApprovalType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for submitting an approval request.
 */
public record SubmitApprovalRequest(
    @NotNull(message = "Approval type is required") ApprovalType approvalType,

    @NotNull(message = "Entity ID is required") UUID entityId,

    @NotNull(message = "Entity type is required") String entityType,

    @NotNull(message = "Requestor ID is required") UUID requestorId,

    Double amount,

    Integer daysCount,

    Integer priority,

    String comments
) {}
