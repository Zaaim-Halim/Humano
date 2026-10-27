package com.humano.domain.enumeration.hr;

/**
 * How a performance improvement plan ended. Recorded only: no outcome changes the employee's status
 * or starts an offboarding — that stays a separate, deliberate HR decision.
 */
public enum ImprovementPlanOutcome {
    /**
     * The expectations were met.
     */
    SUCCESSFUL,

    /**
     * The expectations were not met.
     */
    UNSUCCESSFUL,

    /**
     * Stopped before its end, for example after a change of role or a leave.
     */
    CANCELLED,
}
