package com.humano.domain.enumeration.hr;

/**
 * Where a written review stands in a second-level sign-off, in a cycle that requires one. The
 * review can't be calibrated or shared until it is {@link #isSettled() settled}.
 */
public enum SignOffStatus {
    /**
     * Waiting on the approver (the reviewer's manager, or the configured approval chain).
     */
    PENDING,

    /**
     * Signed off.
     */
    APPROVED,

    /**
     * Sent back to the reviewer with comments; revising the review sends it for sign-off again.
     */
    RETURNED,

    /**
     * Nobody above the reviewer to sign off (the reviewer heads the organisation, or the chain
     * resolves to someone in the review itself).
     */
    WAIVED;

    /** Signed off or waived: the review may move on. */
    public boolean isSettled() {
        return this == APPROVED || this == WAIVED;
    }
}
