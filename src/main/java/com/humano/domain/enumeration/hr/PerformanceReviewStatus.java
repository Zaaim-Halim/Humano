package com.humano.domain.enumeration.hr;

/**
 * Where one employee's review stands inside a review cycle. Reviews written outside a cycle are
 * {@link #SHARED} from the start.
 */
public enum PerformanceReviewStatus {
    /**
     * Created when the cycle's self-assessment phase started; nothing submitted yet.
     */
    NOT_STARTED,

    /**
     * The employee submitted their self-assessment.
     */
    SELF_SUBMITTED,

    /**
     * The reviewer submitted their assessment and rating (with or without a self-assessment).
     */
    MANAGER_SUBMITTED,

    /**
     * The outcome was discussed with the employee and is visible to them.
     */
    SHARED,

    /**
     * Taken out of the cycle before it was shared — by HR, with a reason, or because the employee
     * left. It no longer counts towards the cycle's progress, reminders or analytics, and HR can
     * put it back while the cycle runs.
     */
    EXCLUDED,
}
