package com.humano.domain.enumeration.hr;

/**
 * Where a compensation proposal stands. Pay changes only when an approved proposal is applied.
 */
public enum CompensationProposalStatus {
    /**
     * Being prepared; can be changed, deleted, or returned to from a later step.
     */
    DRAFT,

    /**
     * Sent for approval by someone else holding the compensation permission.
     */
    SUBMITTED,

    /**
     * Approved; waiting to be applied on or after its effective date.
     */
    APPROVED,

    /**
     * The pay, position and bonus were changed, once. Final.
     */
    APPLIED,
}
