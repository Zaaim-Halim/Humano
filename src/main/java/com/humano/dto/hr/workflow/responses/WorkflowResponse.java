package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for workflow information.
 */
public record WorkflowResponse(
    UUID id,
    WorkflowType workflowType,
    UUID entityId,
    String entityType,
    WorkflowStatus status,
    String currentState,
    UUID currentAssigneeId,
    String currentAssigneeName,
    UUID initiatorId,
    String initiatorName,
    Instant startedAt,
    Instant completedAt,
    Instant dueDate,
    List<StateTransitionResponse> transitions,
    Instant createdDate,
    Instant lastModifiedDate
) {
    /**
     * Nested record for state transition information.
     */
    public record StateTransitionResponse(
        UUID id,
        String fromState,
        String toState,
        String reason,
        String transitionedBy,
        Instant transitionedAt
    ) {}
}
