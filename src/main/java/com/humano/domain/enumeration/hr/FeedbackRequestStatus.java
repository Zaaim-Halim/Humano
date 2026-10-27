package com.humano.domain.enumeration.hr;

/**
 * Where a request for feedback on a review stands.
 */
public enum FeedbackRequestStatus {
    /**
     * Nominated by the reviewed employee; the reviewer (or HR) has to approve it first.
     */
    PENDING_APPROVAL,

    /**
     * Sent to the contributor, who hasn't answered yet.
     */
    REQUESTED,

    /**
     * The contributor gave their feedback (they may revise it while the review is being written).
     */
    SUBMITTED,

    /**
     * The contributor declined, or the reviewer turned the nomination down.
     */
    DECLINED,

    /**
     * Withdrawn, or closed unanswered when the review was written or the cycle closed.
     */
    CANCELLED;

    /** Requests that hold a contributor's place on the review. */
    public boolean isActive() {
        return this == PENDING_APPROVAL || this == REQUESTED || this == SUBMITTED;
    }

    /** Requests still waiting on someone. */
    public boolean isOpen() {
        return this == PENDING_APPROVAL || this == REQUESTED;
    }
}
