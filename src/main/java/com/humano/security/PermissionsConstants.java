package com.humano.security;

/**
 * Constants for permissions in the system.
 * Permissions provide granular access control that can be assigned to roles.
 */
public final class PermissionsConstants {

    // General permissions
    public static final String VIEW_DASHBOARD = "VIEW_DASHBOARD";
    public static final String ACCESS_API = "ACCESS_API";

    // User management permissions
    public static final String CREATE_USER = "CREATE_USER";
    public static final String READ_USER = "READ_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String DELETE_USER = "DELETE_USER";

    // HR permissions
    public static final String CREATE_EMPLOYEE = "CREATE_EMPLOYEE";
    public static final String READ_EMPLOYEE = "READ_EMPLOYEE";
    public static final String UPDATE_EMPLOYEE = "UPDATE_EMPLOYEE";
    public static final String DELETE_EMPLOYEE = "DELETE_EMPLOYEE";
    public static final String MANAGE_DEPARTMENTS = "MANAGE_DEPARTMENTS";
    public static final String MANAGE_POSITIONS = "MANAGE_POSITIONS";

    // Employee self-service permissions
    public static final String VIEW_OWN_PROFILE = "VIEW_OWN_PROFILE";
    public static final String UPDATE_OWN_PROFILE = "UPDATE_OWN_PROFILE";
    public static final String VIEW_OWN_PAYSLIPS = "VIEW_OWN_PAYSLIPS";
    public static final String VIEW_OWN_TRAINING = "VIEW_OWN_TRAINING";
    public static final String REGISTER_FOR_TRAINING = "REGISTER_FOR_TRAINING";
    public static final String VIEW_OWN_BENEFITS = "VIEW_OWN_BENEFITS";
    public static final String MANAGE_OWN_BENEFITS = "MANAGE_OWN_BENEFITS";
    public static final String VIEW_OWN_LEAVE = "VIEW_OWN_LEAVE";
    public static final String REQUEST_LEAVE = "REQUEST_LEAVE";
    public static final String VIEW_OWN_ATTENDANCE = "VIEW_OWN_ATTENDANCE";
    public static final String VIEW_OWN_PERFORMANCE = "VIEW_OWN_PERFORMANCE";
    public static final String VIEW_OWN_DOCUMENTS = "VIEW_OWN_DOCUMENTS";
    public static final String UPLOAD_OWN_DOCUMENTS = "UPLOAD_OWN_DOCUMENTS";

    // Payroll permissions
    public static final String CREATE_PAYROLL_RUN = "CREATE_PAYROLL_RUN";
    public static final String VIEW_PAYROLL_RUN = "VIEW_PAYROLL_RUN";
    public static final String APPROVE_PAYROLL = "APPROVE_PAYROLL";
    public static final String PROCESS_PAYROLL = "PROCESS_PAYROLL";
    public static final String MANAGE_PAY_COMPONENTS = "MANAGE_PAY_COMPONENTS";
    public static final String MANAGE_DEDUCTIONS = "MANAGE_DEDUCTIONS";
    public static final String MANAGE_BENEFITS = "MANAGE_BENEFITS";
    public static final String MANAGE_TAX_BRACKETS = "MANAGE_TAX_BRACKETS";
    public static final String VIEW_PAYSLIPS = "VIEW_PAYSLIPS";
    public static final String GENERATE_PAYSLIPS = "GENERATE_PAYSLIPS";

    // Finance permissions
    public static final String MANAGE_BUDGETS = "MANAGE_BUDGETS";
    public static final String VIEW_FINANCIAL_REPORTS = "VIEW_FINANCIAL_REPORTS";
    public static final String MANAGE_EXCHANGE_RATES = "MANAGE_EXCHANGE_RATES";

    // Billing permissions
    public static final String CREATE_INVOICE = "CREATE_INVOICE";
    public static final String VIEW_INVOICE = "VIEW_INVOICE";
    public static final String UPDATE_INVOICE = "UPDATE_INVOICE";
    public static final String DELETE_INVOICE = "DELETE_INVOICE";
    public static final String MANAGE_SUBSCRIPTION_PLANS = "MANAGE_SUBSCRIPTION_PLANS";
    public static final String MANAGE_FEATURES = "MANAGE_FEATURES";
    public static final String MANAGE_COUPONS = "MANAGE_COUPONS";
    public static final String PROCESS_PAYMENTS = "PROCESS_PAYMENTS";

    // Tenant management permissions
    public static final String CREATE_TENANT = "CREATE_TENANT";
    public static final String VIEW_TENANT = "VIEW_TENANT";
    public static final String UPDATE_TENANT = "UPDATE_TENANT";
    public static final String DELETE_TENANT = "DELETE_TENANT";
    public static final String MANAGE_ORGANIZATIONS = "MANAGE_ORGANIZATIONS";

    // Reporting permissions
    public static final String VIEW_REPORTS = "VIEW_REPORTS";
    public static final String CREATE_REPORTS = "CREATE_REPORTS";
    public static final String EXPORT_REPORTS = "EXPORT_REPORTS";
    public static final String SCHEDULE_REPORTS = "SCHEDULE_REPORTS";

    // System administration permissions
    public static final String SYSTEM_CONFIGURATION = "SYSTEM_CONFIGURATION";
    public static final String AUDIT_LOG_ACCESS = "AUDIT_LOG_ACCESS";
    public static final String MANAGE_INTEGRATIONS = "MANAGE_INTEGRATIONS";

    private PermissionsConstants() {
    }
}
