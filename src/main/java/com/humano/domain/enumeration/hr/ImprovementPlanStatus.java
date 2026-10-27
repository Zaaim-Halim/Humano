package com.humano.domain.enumeration.hr;

/**
 * Where a performance improvement plan stands.
 */
public enum ImprovementPlanStatus {
    /**
     * Being written by the manager or HR; the employee doesn't see it yet.
     */
    DRAFT,

    /**
     * Shared with the employee and running until its end date; check-ins are recorded against it.
     */
    ACTIVE,

    /**
     * Ended, with an outcome. Kept as a record; it never changes the employee's status.
     */
    CLOSED,
}
