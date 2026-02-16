package com.humano.domain.enumeration.hr;

/**
 * Represents the type of notification.
 */
public enum NotificationType {
    /**
     * Approval request notification.
     */
    APPROVAL_REQUIRED,

    /**
     * Approval decision notification.
     */
    APPROVAL_DECISION,

    /**
     * Task assignment notification.
     */
    TASK_ASSIGNED,

    /**
     * Task completion notification.
     */
    TASK_COMPLETED,

    /**
     * Deadline approaching notification.
     */
    DEADLINE_APPROACHING,

    /**
     * Deadline exceeded notification.
     */
    DEADLINE_EXCEEDED,

    /**
     * Workflow completion notification.
     */
    WORKFLOW_COMPLETED,

    /**
     * Escalation notification.
     */
    ESCALATION,

    /**
     * Reminder notification.
     */
    REMINDER,

    /**
     * General information notification.
     */
    INFO,

    /**
     * Welcome notification for new employees.
     */
    WELCOME,

    /**
     * Training enrollment notification.
     */
    TRAINING_ENROLLMENT,

    /**
     * Performance review notification.
     */
    PERFORMANCE_REVIEW,
}
