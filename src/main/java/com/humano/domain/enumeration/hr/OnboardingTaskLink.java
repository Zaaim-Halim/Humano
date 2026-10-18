package com.humano.domain.enumeration.hr;

/**
 * The employee record that shows an onboarding task is done. A linked task
 * shows whether that record exists, so ticking it off can be checked against
 * the employee's file.
 */
public enum OnboardingTaskLink {
    /**
     * The employee's user account is activated.
     */
    ACCOUNT,

    /**
     * At least one document is on the employee's file.
     */
    DOCUMENTS,

    /**
     * The employee has a benefit enrollment (pending or active).
     */
    BENEFITS,

    /**
     * The employee has completed trainings and none left planned.
     */
    TRAININGS,
}
