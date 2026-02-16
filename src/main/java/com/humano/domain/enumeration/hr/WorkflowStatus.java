package com.humano.domain.enumeration.hr;

/**
 * Represents the status of a workflow instance.
 */
public enum WorkflowStatus {
    /**
     * Workflow has been created but not started.
     */
    DRAFT,

    /**
     * Workflow is currently in progress.
     */
    IN_PROGRESS,

    /**
     * Workflow is waiting for approval.
     */
    PENDING_APPROVAL,

    /**
     * Workflow has been approved.
     */
    APPROVED,

    /**
     * Workflow has been rejected.
     */
    REJECTED,

    /**
     * Workflow has been completed successfully.
     */
    COMPLETED,

    /**
     * Workflow has been cancelled.
     */
    CANCELLED,

    /**
     * Workflow has failed.
     */
    FAILED,

    /**
     * Workflow is on hold.
     */
    ON_HOLD,

    /**
     * Workflow has been escalated.
     */
    ESCALATED,
}
