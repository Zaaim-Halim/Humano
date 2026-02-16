package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for processing an approval decision.
 */
public record ApprovalDecisionRequest(
    @NotNull(message = "Decision is required") ApprovalDecision decision,

    String comments
) {
    public enum ApprovalDecision {
        APPROVE,
        REJECT,
        REQUEST_MORE_INFO,
        DELEGATE,
    }
}
