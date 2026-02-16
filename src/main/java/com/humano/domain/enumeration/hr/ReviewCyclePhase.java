package com.humano.domain.enumeration.hr;

/**
 * Represents the phase of a performance review cycle.
 */
public enum ReviewCyclePhase {
    /**
     * Review cycle is in draft state.
     */
    DRAFT,

    /**
     * Self-assessment phase.
     */
    SELF_ASSESSMENT,

    /**
     * Manager review phase.
     */
    MANAGER_REVIEW,

    /**
     * Calibration phase.
     */
    CALIBRATION,

    /**
     * Feedback delivery phase.
     */
    FEEDBACK_DELIVERY,

    /**
     * Goal setting phase.
     */
    GOAL_SETTING,

    /**
     * Review cycle is completed.
     */
    COMPLETED,

    /**
     * Review cycle is archived.
     */
    ARCHIVED,
}
