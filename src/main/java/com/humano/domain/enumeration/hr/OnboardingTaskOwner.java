package com.humano.domain.enumeration.hr;

/**
 * Who an onboarding checklist task is assigned to when an onboarding starts.
 */
public enum OnboardingTaskOwner {
    /**
     * The new hire themselves.
     */
    NEW_HIRE,

    /**
     * The new hire's manager (unassigned when they have none).
     */
    MANAGER,

    /**
     * HR / IT — left unassigned on the task.
     */
    HR,
}
