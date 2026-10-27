package com.humano.domain.enumeration.hr;

/**
 * Where an employee's goal stands.
 */
public enum GoalStatus {
    /**
     * Proposed by the employee; waiting for their manager to agree it.
     */
    DRAFT,

    /**
     * Agreed with the manager (or set by them); progress is checked in against it.
     */
    ACTIVE,

    /**
     * Done. Rated in the review whose period covers its due date.
     */
    COMPLETED,

    /**
     * No longer pursued, with a reason. Never rated.
     */
    CANCELLED,
}
