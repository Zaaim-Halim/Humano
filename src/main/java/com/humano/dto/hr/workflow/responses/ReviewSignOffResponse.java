package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.SignOffStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A review's second-level sign-off for its page: where it stands and, while it waits, on whom
 * ({@code approvalRequestId} is what the approver decides on). {@code canDecide} is true for the
 * approver it waits on.
 */
public record ReviewSignOffResponse(
    SignOffStatus status,
    UUID approvalRequestId,
    UUID approverId,
    String approverName,
    int level,
    int totalLevels,
    Instant submittedAt,
    Instant dueDate,
    boolean canDecide
) {}
