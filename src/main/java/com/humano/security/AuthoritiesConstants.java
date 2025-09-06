package com.humano.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    // HR roles
    public static final String HR_MANAGER = "ROLE_HR_MANAGER";
    public static final String HR_SPECIALIST = "ROLE_HR_SPECIALIST";

    // Payroll roles
    public static final String PAYROLL_ADMIN = "ROLE_PAYROLL_ADMIN";
    public static final String PAYROLL_SPECIALIST = "ROLE_PAYROLL_SPECIALIST";
    public static final String PAYROLL_VIEWER = "ROLE_PAYROLL_VIEWER";

    // Finance roles
    public static final String FINANCE_MANAGER = "ROLE_FINANCE_MANAGER";
    public static final String FINANCE_SPECIALIST = "ROLE_FINANCE_SPECIALIST";

    // Billing roles
    public static final String BILLING_ADMIN = "ROLE_BILLING_ADMIN";
    public static final String BILLING_SPECIALIST = "ROLE_BILLING_SPECIALIST";

    // Tenant management roles
    public static final String TENANT_ADMIN = "ROLE_TENANT_ADMIN";
    public static final String ORGANIZATION_ADMIN = "ROLE_ORGANIZATION_ADMIN";

    // Employee roles
    public static final String EMPLOYEE = "ROLE_EMPLOYEE";
    public static final String MANAGER = "ROLE_MANAGER";

    // Reporting roles
    public static final String REPORT_VIEWER = "ROLE_REPORT_VIEWER";
    public static final String REPORT_ADMIN = "ROLE_REPORT_ADMIN";

    private AuthoritiesConstants() {
    }
}
