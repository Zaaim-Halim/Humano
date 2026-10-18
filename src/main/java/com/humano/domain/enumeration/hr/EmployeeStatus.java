package com.humano.domain.enumeration.hr;

/**
 * Employment status. ONBOARDING is set while the employee's onboarding runs (by
 * {@code EmployeeLifecycleWorkflowService}) and counts as employed, like ACTIVE.
 */
public enum EmployeeStatus {
    ACTIVE,
    ONBOARDING,
    INACTIVE,
    TERMINATED;

    /** Statuses of people who currently work here (paid, scheduled, on team views). */
    public static final java.util.List<EmployeeStatus> WORKING = java.util.List.of(ACTIVE, ONBOARDING);
}
