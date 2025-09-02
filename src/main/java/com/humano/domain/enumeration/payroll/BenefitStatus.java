package com.humano.domain.enumeration.payroll;

/**
 * Represents the current status of an employee's benefit enrollment.
 * <p>
 * Used to track the lifecycle of employee benefits through various stages from
 * initial enrollment to termination. Different statuses affect eligibility,
 * billing, and coverage under the benefit plan.
 */
public enum BenefitStatus {
    /**
     * Benefit is currently active and in force.
     * Employee is covered and the benefit is being applied/provided.
     */
    ACTIVE,

    /**
     * Benefit enrollment is pending approval or waiting period.
     * Coverage has not yet begun but the enrollment process has started.
     */
    PENDING,

    /**
     * Benefit has been terminated and is no longer in effect.
     * Used when an employee's coverage has ended permanently.
     */
    TERMINATED,

    /**
     * Benefit is temporarily suspended but not terminated.
     * Coverage is paused but can be reinstated without re-enrollment.
     */
    SUSPENDED,

    /**
     * Employee is in the waiting period before benefit begins.
     * Enrollment is approved but coverage has not yet started due to eligibility rules.
     */
    WAITING_PERIOD,

    /**
     * Employee is in open enrollment period for benefit selection.
     * Period when employees can enroll in or modify their benefits.
     */
    OPEN_ENROLLMENT,

    /**
     * Employee is on COBRA continuation coverage.
     * Former employee continuing benefits at their own expense.
     */
    COBRA,

    /**
     * Employee is retired but still receiving benefits.
     * Used for retiree benefits that continue after employment ends.
     */
    RETIRED,

    /**
     * Employee has declined the benefit.
     * Employee has actively chosen not to participate in this benefit.
     */
    DECLINED
}
