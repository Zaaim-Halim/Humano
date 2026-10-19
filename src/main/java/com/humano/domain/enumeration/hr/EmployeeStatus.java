package com.humano.domain.enumeration.hr;

/**
 * Employment status. ONBOARDING and OFFBOARDING are set while the employee's onboarding or
 * offboarding runs (by {@code EmployeeLifecycleWorkflowService}) and count as employed, like
 * ACTIVE; an offboarding employee becomes TERMINATED the day after their last working day.
 */
public enum EmployeeStatus {
    ACTIVE,
    ONBOARDING,
    OFFBOARDING,
    INACTIVE,
    TERMINATED;

    /** Statuses of people who currently work here (paid, scheduled, on team views). */
    public static final java.util.List<EmployeeStatus> WORKING = java.util.List.of(ACTIVE, ONBOARDING, OFFBOARDING);
}
