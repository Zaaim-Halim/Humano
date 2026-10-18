package com.humano.service.billing.entitlement;

/**
 * Codes of the plan features the application enforces — they must match {@code Feature.code} in the
 * plan catalog. The others in the catalog (directory, dashboards, dedicated database, support, SLA…)
 * describe the offer and restrict nothing.
 */
public final class FeatureCodes {

    /** How many people can be managed (everyone not terminated). */
    public static final String EMPLOYEES = "EMPLOYEES";
    /** How many people may hold an HR or payroll admin role ({@link #SEAT_ROLES}). */
    public static final String ADMIN_SEATS = "ADMIN_SEATS";
    /** Payroll runs, payslips, pay rules and the payroll set-up behind them. */
    public static final String PAYROLL = "PAYROLL";
    /** Timesheets, attendance, the time clock and overtime records. */
    public static final String TIMESHEETS = "TIMESHEETS";
    /** Approval chains of more than one level (otherwise only the first approver decides). */
    public static final String APPROVAL_CHAINS = "APPROVAL_CHAINS";
    /** Bonuses and benefits (base salaries are part of every plan: payroll needs them). */
    public static final String COMPENSATION = "COMPENSATION";
    /** Performance reviews and review cycles. */
    public static final String PERFORMANCE = "PERFORMANCE";
    /** Training and certifications. */
    public static final String TRAINING = "TRAINING";
    /** Storage for files and documents, limited in GB (the catalog's "Document storage"). */
    public static final String STORAGE = "DOCUMENTS";

    /** The roles that take an {@link #ADMIN_SEATS} seat: the people who do HR or payroll work. */
    public static final java.util.Set<String> SEAT_ROLES = java.util.Set.of(
        com.humano.security.AuthoritiesConstants.ADMIN,
        com.humano.security.AuthoritiesConstants.TENANT_ADMIN,
        com.humano.security.AuthoritiesConstants.HR_MANAGER,
        com.humano.security.AuthoritiesConstants.HR_SPECIALIST,
        com.humano.security.AuthoritiesConstants.PAYROLL_ADMIN,
        com.humano.security.AuthoritiesConstants.PAYROLL_SPECIALIST
    );

    private FeatureCodes() {}
}
