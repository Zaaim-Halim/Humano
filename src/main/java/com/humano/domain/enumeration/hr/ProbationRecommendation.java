package com.humano.domain.enumeration.hr;

/**
 * The reviewer's recommendation at the end of an employee's probation. A recommendation only: it
 * never confirms the employee or changes their probation by itself, and it is never shown to them.
 */
public enum ProbationRecommendation {
    /**
     * Confirm the employee in their role.
     */
    CONFIRM,

    /**
     * Extend the probation.
     */
    EXTEND,

    /**
     * Don't confirm the employee.
     */
    NOT_CONFIRM,
}
