package com.humano.domain.enumeration.hr;

/**
 * Represents the type of workflow in the HR system.
 */
public enum WorkflowType {
    /**
     * Employee onboarding workflow.
     */
    ONBOARDING,

    /**
     * Employee offboarding workflow.
     */
    OFFBOARDING,

    /**
     * Leave request approval workflow.
     */
    LEAVE_APPROVAL,

    /**
     * Expense claim approval workflow.
     */
    EXPENSE_APPROVAL,

    /**
     * Overtime approval workflow.
     */
    OVERTIME_APPROVAL,

    /**
     * Training enrollment workflow.
     */
    TRAINING_ENROLLMENT,

    /**
     * Performance review cycle workflow.
     */
    PERFORMANCE_REVIEW_CYCLE,

    /**
     * Position/department transfer workflow.
     */
    TRANSFER,

    /**
     * Timesheet approval workflow.
     */
    TIMESHEET_APPROVAL,
}
