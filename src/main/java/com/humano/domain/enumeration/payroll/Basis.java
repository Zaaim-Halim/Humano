package com.humano.domain.enumeration.payroll;

/**
 * Basis of compensation for payroll calculation.
 * <ul>
 *   <li>MONTHLY: Salary is paid per month.</li>
 *   <li>ANNUAL: Salary is paid per year.</li>
 *   <li>HOURLY: Compensation is based on hours worked.</li>
 * </ul>
 */
public enum Basis {
    /**
     * Monthly salary basis.
     */
    MONTHLY,
    /**
     * Annual salary basis.
     */
    ANNUAL,
    /**
     * Hourly wage basis.
     */
    HOURLY
}
