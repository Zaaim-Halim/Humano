package com.humano.domain.enumeration.hr;

/**
 * The reviewer's view of how far an employee could grow beyond their current role — the second
 * axis, with the rating, of the calibration 9-box. Confidential: never shown to the employee.
 */
public enum ReviewPotential {
    /**
     * Well placed in the current role; growth mostly within it.
     */
    LOW,

    /**
     * Could take on a broader role or the next level over time.
     */
    MODERATE,

    /**
     * Ready, or nearly ready, for a significantly bigger role.
     */
    HIGH,
}
