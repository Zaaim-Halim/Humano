package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for pending approval summary.
 */
public record PendingApprovalSummary(
    UUID approvalRequestId,
    ApprovalType approvalType,
    UUID entityId,
    String entityType,
    String entityDescription,
    UUID requestorId,
    String requestorName,
    WorkflowStatus status,
    Integer priority,
    Double amount,
    Integer daysCount,
    Instant submittedAt,
    Instant dueDate,
    boolean isOverdue,
    long daysWaiting
) {}
