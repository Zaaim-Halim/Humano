package com.humano.domain.enumeration.hr;

/**
 * Why a review's final rating changed after the reviewer gave it.
 */
public enum RatingChangeSource {
    /**
     * Adjusted in the cycle's calibration, to keep ratings consistent across teams.
     */
    CALIBRATION,

    /**
     * Changed when HR resolved the employee's dispute of the review.
     */
    DISPUTE_RESOLUTION,
}
