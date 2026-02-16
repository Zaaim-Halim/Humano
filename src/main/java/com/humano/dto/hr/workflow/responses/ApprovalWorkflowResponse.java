package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for approval workflow details.
 */
public record ApprovalWorkflowResponse(
    UUID approvalRequestId,
    UUID workflowId,
    ApprovalType approvalType,
    UUID entityId,
    String entityType,
    WorkflowStatus status,
    UUID requestorId,
    String requestorName,
    UUID currentApproverId,
    String currentApproverName,
    Integer currentLevel,
    Integer totalLevels,
    Double amount,
    Integer daysCount,
    Integer priority,
    Instant submittedAt,
    Instant decidedAt,
    Instant dueDate,
    String approverComments,
    List<ApprovalHistoryItem> approvalHistory,
    Instant createdDate,
    Instant lastModifiedDate
) {
    public record ApprovalHistoryItem(
        Integer level,
        UUID approverId,
        String approverName,
        String decision,
        String comments,
        Instant decidedAt
    ) {}
}
