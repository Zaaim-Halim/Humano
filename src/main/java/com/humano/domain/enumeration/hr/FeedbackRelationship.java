package com.humano.domain.enumeration.hr;

/**
 * How a contributor asked for feedback on a review works with the reviewed employee.
 */
public enum FeedbackRelationship {
    /**
     * A colleague at the same level.
     */
    PEER,

    /**
     * Someone who reports to the reviewed employee (upward feedback).
     */
    DIRECT_REPORT,

    /**
     * The lead of a project the employee worked on.
     */
    PROJECT_LEAD,

    /**
     * The manager of the employee's manager.
     */
    SKIP_LEVEL,

    /**
     * A colleague from another team the employee works with.
     */
    CROSS_FUNCTIONAL,

    /**
     * The employee's reviewer before the review was reassigned.
     */
    PREVIOUS_MANAGER,

    /**
     * Any other working relationship.
     */
    OTHER,
}
