package com.humano.domain.enumeration.hr;

/**
 * Represents the current status of an employee process (onboarding or offboarding).
 */
public enum EmployeeProcessStatus {
    /**
     * Process has been created but not started.
     */
    PLANNED,

    /**
     * Process is currently in progress.
     */
    IN_PROGRESS,

    /**
     * Process has been completed successfully.
     */
    COMPLETED,

    /**
     * Process has been delayed.
     */
    DELAYED,

    /**
     * Process has been cancelled.
     */
    CANCELLED
}
