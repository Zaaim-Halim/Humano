package com.humano.domain.enumeration.hr;

/**
 * Whether the reviewed employee sees the feedback their contributors gave, once the review is
 * shared. The reviewer and HR always see it, with names.
 */
public enum FeedbackVisibility {
    /**
     * Input for the reviewer only; the employee never sees it.
     */
    MANAGER_ONLY,

    /**
     * Shared with the employee, with each contributor's name and relationship.
     */
    SHARED_NAMED,

    /**
     * Shared with the employee without names, and only when enough contributors answered to keep
     * them anonymous.
     */
    SHARED_ANONYMOUS,
}
