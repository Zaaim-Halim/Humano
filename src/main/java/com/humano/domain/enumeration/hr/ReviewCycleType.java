package com.humano.domain.enumeration.hr;

/**
 * What a review cycle is for, which decides who it reviews and whether it may overlap others.
 */
public enum ReviewCycleType {
    /**
     * The company's regular (annual or semi-annual) review of its departments. Two regular cycles
     * may not run at the same time for the same people.
     */
    REGULAR,

    /**
     * End-of-probation reviews: employees not yet confirmed whose probation ends within the review
     * period. The reviewer recommends confirming, extending or not confirming — a recommendation
     * only; HR records the decision on the employee.
     */
    PROBATION,

    /**
     * A review of named employees outside the regular calendar (a project's end, a role change).
     */
    AD_HOC,
}
