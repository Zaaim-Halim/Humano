package com.humano.domain.enumeration.hr;

/**
 * Who answers a review template's question.
 */
public enum ReviewQuestionAudience {
    /**
     * The reviewed employee, in their self-assessment.
     */
    EMPLOYEE,

    /**
     * The reviewer, in the review. The employee reads the answer once the review is shared.
     */
    REVIEWER,
}
