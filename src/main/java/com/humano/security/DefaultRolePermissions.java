package com.humano.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for the default <strong>role → permission</strong> mapping in a
 * business tenant.
 * <p>
 * Every tenant DB is seeded from this map (see {@code TenantInitializationService}); the
 * runtime authorization checks ({@code @RequirePermission} via
 * {@link AuthorityPermissionService}) read the resulting rows back out of that tenant's DB.
 * Keeping seeding and enforcement anchored to the same constants
 * ({@link AuthoritiesConstants} / {@link PermissionsConstants}) is what prevents the
 * historical drift where seeded codes ({@code EMPLOYEE_READ}) never matched gated codes
 * ({@code READ_EMPLOYEE}).
 * <p>
 * The grants below mirror the "Typically granted to" guidance documented on each constant in
 * {@link PermissionsConstants}. {@link AuthoritiesConstants#ADMIN} implicitly receives every
 * permission, and every (non-anonymous) role receives {@link PermissionsConstants#VIEW_DASHBOARD}
 * and {@link PermissionsConstants#ACCESS_API}.
 * <p>
 * Platform/SaaS-owner grants are intentionally <em>not</em> here — they live in
 * {@link PlatformRolePermissions} and are seeded only into the platform tenant.
 */
public final class DefaultRolePermissions {

    /** permission name → roles that hold it (built from the documented intent, then inverted). */
    private static final Map<String, Set<String>> PERMISSION_TO_ROLES = new LinkedHashMap<>();

    /** role name → permissions it grants (the inverted, public view). */
    private static final Map<String, Set<String>> ROLE_TO_PERMISSIONS;

    /** all distinct permission names referenced by the default mapping. */
    private static final Set<String> ALL_PERMISSIONS;

    private static void grant(String permission, String... roles) {
        PERMISSION_TO_ROLES.computeIfAbsent(permission, k -> new LinkedHashSet<>()).addAll(Set.of(roles));
    }

    static {
        // ---- General (granted to every role below via the post-processing step) ----
        grant(PermissionsConstants.VIEW_DASHBOARD);
        grant(PermissionsConstants.ACCESS_API);

        // ---- User management ----
        grant(PermissionsConstants.CREATE_USER, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.READ_USER, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.UPDATE_USER, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.DELETE_USER);

        // ---- HR ----
        grant(
            PermissionsConstants.CREATE_EMPLOYEE,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.ONBOARDING_SPECIALIST
        );
        grant(
            PermissionsConstants.READ_EMPLOYEE,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.MANAGER,
            AuthoritiesConstants.PAYROLL_ADMIN
        );
        grant(PermissionsConstants.UPDATE_EMPLOYEE, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.DELETE_EMPLOYEE, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_DEPARTMENTS, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.ORGANIZATION_ADMIN);
        grant(
            PermissionsConstants.VIEW_DEPARTMENTS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.MANAGER,
            AuthoritiesConstants.ORGANIZATION_ADMIN
        );
        grant(PermissionsConstants.MANAGE_POSITIONS, AuthoritiesConstants.HR_MANAGER);
        grant(
            PermissionsConstants.VIEW_POSITIONS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.MANAGER
        );
        grant(PermissionsConstants.MANAGE_ORGANIZATIONAL_UNITS, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.ORGANIZATION_ADMIN);
        grant(
            PermissionsConstants.VIEW_ORGANIZATIONAL_UNITS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.MANAGER,
            AuthoritiesConstants.ORGANIZATION_ADMIN
        );
        grant(PermissionsConstants.VIEW_TEAM_MEMBERS, AuthoritiesConstants.MANAGER, AuthoritiesConstants.DEPARTMENT_HEAD);

        // ---- HR resource catalogs (Phase 2 migration) ----
        grant(PermissionsConstants.MANAGE_SKILLS, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_PROJECTS, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_SURVEYS, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.VIEW_SURVEYS, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.MANAGE_EMPLOYEE_DOCUMENTS, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.VIEW_EMPLOYEE_DOCUMENTS, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.HR_SPECIALIST);

        // ---- Recruitment ----
        grant(PermissionsConstants.CREATE_JOB_POSTING, AuthoritiesConstants.RECRUITER, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.VIEW_JOB_POSTING, AuthoritiesConstants.RECRUITER, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.MANAGE_JOB_POSTINGS, AuthoritiesConstants.RECRUITER, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_CANDIDATES, AuthoritiesConstants.RECRUITER);
        grant(PermissionsConstants.MANAGE_INTERVIEWS, AuthoritiesConstants.RECRUITER, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.GENERATE_OFFER_LETTERS, AuthoritiesConstants.RECRUITER, AuthoritiesConstants.HR_MANAGER);

        // ---- Onboarding ----
        grant(PermissionsConstants.MANAGE_ONBOARDING, AuthoritiesConstants.ONBOARDING_SPECIALIST, AuthoritiesConstants.HR_SPECIALIST);
        grant(
            PermissionsConstants.VIEW_ONBOARDING_PROGRESS,
            AuthoritiesConstants.ONBOARDING_SPECIALIST,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.MANAGER
        );

        // ---- Employee self-service ----
        for (String selfServe : new String[] {
            PermissionsConstants.VIEW_OWN_PROFILE,
            PermissionsConstants.UPDATE_OWN_PROFILE,
            PermissionsConstants.VIEW_OWN_PAYSLIPS,
            PermissionsConstants.VIEW_OWN_TRAINING,
            PermissionsConstants.REGISTER_FOR_TRAINING,
            PermissionsConstants.VIEW_OWN_BENEFITS,
            PermissionsConstants.MANAGE_OWN_BENEFITS,
            PermissionsConstants.VIEW_OWN_LEAVE,
            PermissionsConstants.REQUEST_LEAVE,
            PermissionsConstants.VIEW_OWN_ATTENDANCE,
            PermissionsConstants.VIEW_OWN_PERFORMANCE,
            PermissionsConstants.VIEW_OWN_DOCUMENTS,
            PermissionsConstants.UPLOAD_OWN_DOCUMENTS,
            PermissionsConstants.SUBMIT_EXPENSE_CLAIM,
            PermissionsConstants.VIEW_OWN_EXPENSES,
            PermissionsConstants.SUBMIT_TIMESHEET,
            PermissionsConstants.VIEW_OWN_TIMESHEETS,
        }) {
            grant(selfServe, AuthoritiesConstants.EMPLOYEE);
        }

        // ---- Attendance & time tracking ----
        grant(
            PermissionsConstants.VIEW_ATTENDANCE,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.ATTENDANCE_ADMIN
        );
        grant(
            PermissionsConstants.MANAGE_ATTENDANCE,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.ATTENDANCE_ADMIN
        );
        grant(PermissionsConstants.CONFIGURE_ATTENDANCE_POLICIES, AuthoritiesConstants.ATTENDANCE_ADMIN);
        grant(
            PermissionsConstants.VIEW_TIMESHEETS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.MANAGER,
            AuthoritiesConstants.TIMESHEET_APPROVER
        );
        grant(PermissionsConstants.MANAGE_TIMESHEETS, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.APPROVE_TIMESHEETS, AuthoritiesConstants.MANAGER, AuthoritiesConstants.TIMESHEET_APPROVER);
        grant(PermissionsConstants.MANAGE_SHIFTS, AuthoritiesConstants.ATTENDANCE_ADMIN, AuthoritiesConstants.MANAGER);
        grant(PermissionsConstants.APPROVE_OVERTIME, AuthoritiesConstants.MANAGER, AuthoritiesConstants.TIMESHEET_APPROVER);
        grant(
            PermissionsConstants.VIEW_OVERTIME_RECORDS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.MANAGER,
            AuthoritiesConstants.TIMESHEET_APPROVER
        );
        grant(PermissionsConstants.MANAGE_OVERTIME_RECORDS, AuthoritiesConstants.HR_MANAGER);

        // ---- Leave management ----
        grant(
            PermissionsConstants.VIEW_LEAVE_REQUESTS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.LEAVE_ADMIN,
            AuthoritiesConstants.MANAGER
        );
        grant(PermissionsConstants.MANAGE_LEAVE_REQUESTS, AuthoritiesConstants.HR_MANAGER, AuthoritiesConstants.LEAVE_ADMIN);
        grant(PermissionsConstants.APPROVE_LEAVE, AuthoritiesConstants.MANAGER, AuthoritiesConstants.LEAVE_APPROVER);
        grant(PermissionsConstants.CONFIGURE_LEAVE_POLICIES, AuthoritiesConstants.LEAVE_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.ADJUST_LEAVE_BALANCE, AuthoritiesConstants.LEAVE_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_HOLIDAYS, AuthoritiesConstants.LEAVE_ADMIN, AuthoritiesConstants.HR_MANAGER);

        // ---- Training & development ----
        grant(PermissionsConstants.CREATE_TRAINING, AuthoritiesConstants.TRAINING_ADMIN);
        grant(
            PermissionsConstants.VIEW_TRAINING,
            AuthoritiesConstants.TRAINING_ADMIN,
            AuthoritiesConstants.TRAINING_COORDINATOR,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST
        );
        grant(PermissionsConstants.MANAGE_TRAINING, AuthoritiesConstants.TRAINING_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.SCHEDULE_TRAINING, AuthoritiesConstants.TRAINING_COORDINATOR, AuthoritiesConstants.TRAINING_ADMIN);
        grant(
            PermissionsConstants.MANAGE_TRAINING_ENROLLMENTS,
            AuthoritiesConstants.TRAINING_COORDINATOR,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST
        );
        grant(PermissionsConstants.MANAGE_CERTIFICATIONS, AuthoritiesConstants.TRAINING_ADMIN, AuthoritiesConstants.HR_SPECIALIST);
        grant(PermissionsConstants.MANAGE_TRAINING_BUDGET, AuthoritiesConstants.TRAINING_ADMIN, AuthoritiesConstants.HR_MANAGER);

        // ---- Performance management ----
        grant(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES, AuthoritiesConstants.PERFORMANCE_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_PERFORMANCE_FRAMEWORKS, AuthoritiesConstants.PERFORMANCE_ADMIN);
        grant(PermissionsConstants.SUBMIT_PERFORMANCE_REVIEW, AuthoritiesConstants.MANAGER, AuthoritiesConstants.PERFORMANCE_REVIEWER);
        grant(
            PermissionsConstants.VIEW_PERFORMANCE_REVIEWS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.PERFORMANCE_ADMIN
        );
        grant(
            PermissionsConstants.MANAGE_PERFORMANCE_REVIEWS,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST,
            AuthoritiesConstants.PERFORMANCE_ADMIN
        );
        grant(PermissionsConstants.VIEW_TEAM_PERFORMANCE, AuthoritiesConstants.MANAGER, AuthoritiesConstants.DEPARTMENT_HEAD);
        grant(PermissionsConstants.MANAGE_CALIBRATION, AuthoritiesConstants.PERFORMANCE_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_GOALS, AuthoritiesConstants.MANAGER, AuthoritiesConstants.PERFORMANCE_REVIEWER);

        // ---- Benefits administration ----
        grant(PermissionsConstants.CONFIGURE_BENEFIT_PLANS, AuthoritiesConstants.BENEFITS_ADMIN);
        grant(
            PermissionsConstants.MANAGE_HEALTH_INSURANCE,
            AuthoritiesConstants.BENEFITS_ADMIN,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST
        );
        grant(
            PermissionsConstants.VIEW_HEALTH_INSURANCE,
            AuthoritiesConstants.BENEFITS_ADMIN,
            AuthoritiesConstants.BENEFITS_SPECIALIST,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST
        );
        grant(PermissionsConstants.MANAGE_OPEN_ENROLLMENT, AuthoritiesConstants.BENEFITS_ADMIN);
        grant(
            PermissionsConstants.PROCESS_BENEFIT_ENROLLMENTS,
            AuthoritiesConstants.BENEFITS_SPECIALIST,
            AuthoritiesConstants.BENEFITS_ADMIN
        );
        grant(
            PermissionsConstants.VIEW_EMPLOYEE_BENEFITS,
            AuthoritiesConstants.BENEFITS_ADMIN,
            AuthoritiesConstants.BENEFITS_SPECIALIST,
            AuthoritiesConstants.HR_MANAGER
        );
        grant(PermissionsConstants.MANAGE_BENEFIT_VENDORS, AuthoritiesConstants.BENEFITS_ADMIN);

        // ---- Expense management ----
        grant(
            PermissionsConstants.VIEW_EXPENSE_CLAIMS,
            AuthoritiesConstants.EXPENSE_ADMIN,
            AuthoritiesConstants.MANAGER,
            AuthoritiesConstants.FINANCE_MANAGER,
            AuthoritiesConstants.HR_MANAGER,
            AuthoritiesConstants.HR_SPECIALIST
        );
        grant(PermissionsConstants.MANAGE_EXPENSE_CLAIMS, AuthoritiesConstants.EXPENSE_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.APPROVE_EXPENSE_CLAIMS, AuthoritiesConstants.MANAGER, AuthoritiesConstants.EXPENSE_APPROVER);
        grant(PermissionsConstants.CONFIGURE_EXPENSE_POLICIES, AuthoritiesConstants.EXPENSE_ADMIN, AuthoritiesConstants.FINANCE_MANAGER);
        grant(PermissionsConstants.MANAGE_CORPORATE_CARDS, AuthoritiesConstants.EXPENSE_ADMIN, AuthoritiesConstants.FINANCE_MANAGER);

        // ---- Payroll ----
        grant(PermissionsConstants.CREATE_PAYROLL_RUN, AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.PAYROLL_SPECIALIST);
        grant(
            PermissionsConstants.VIEW_PAYROLL_RUN,
            AuthoritiesConstants.PAYROLL_ADMIN,
            AuthoritiesConstants.PAYROLL_SPECIALIST,
            AuthoritiesConstants.PAYROLL_VIEWER
        );
        grant(PermissionsConstants.APPROVE_PAYROLL, AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.FINANCE_MANAGER);
        grant(PermissionsConstants.PROCESS_PAYROLL, AuthoritiesConstants.PAYROLL_ADMIN);
        grant(PermissionsConstants.MANAGE_PAYROLL_INPUTS, AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.PAYROLL_SPECIALIST);
        grant(PermissionsConstants.MANAGE_PAY_COMPONENTS, AuthoritiesConstants.PAYROLL_ADMIN);
        grant(PermissionsConstants.MANAGE_DEDUCTIONS, AuthoritiesConstants.PAYROLL_ADMIN);
        grant(
            PermissionsConstants.MANAGE_BENEFITS,
            AuthoritiesConstants.PAYROLL_ADMIN,
            AuthoritiesConstants.BENEFITS_ADMIN,
            AuthoritiesConstants.HR_MANAGER
        );
        grant(PermissionsConstants.MANAGE_TAX_BRACKETS, AuthoritiesConstants.PAYROLL_ADMIN);
        grant(
            PermissionsConstants.VIEW_PAYSLIPS,
            AuthoritiesConstants.PAYROLL_ADMIN,
            AuthoritiesConstants.PAYROLL_SPECIALIST,
            AuthoritiesConstants.PAYROLL_VIEWER,
            AuthoritiesConstants.HR_MANAGER
        );
        grant(PermissionsConstants.GENERATE_PAYSLIPS, AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.PAYROLL_SPECIALIST);
        grant(PermissionsConstants.MANAGE_COMPENSATION, AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.MANAGE_BONUSES, AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.HR_MANAGER);
        grant(PermissionsConstants.CONFIGURE_PAYROLL_CALENDAR, AuthoritiesConstants.PAYROLL_ADMIN);

        // ---- Finance ----
        grant(PermissionsConstants.MANAGE_BUDGETS, AuthoritiesConstants.FINANCE_MANAGER);
        grant(
            PermissionsConstants.VIEW_FINANCIAL_REPORTS,
            AuthoritiesConstants.FINANCE_MANAGER,
            AuthoritiesConstants.FINANCE_SPECIALIST,
            AuthoritiesConstants.EXECUTIVE
        );
        grant(PermissionsConstants.MANAGE_EXCHANGE_RATES, AuthoritiesConstants.FINANCE_MANAGER, AuthoritiesConstants.PAYROLL_ADMIN);
        grant(PermissionsConstants.MANAGE_COST_CENTERS, AuthoritiesConstants.FINANCE_MANAGER);

        // ---- Billing ----
        grant(PermissionsConstants.CREATE_INVOICE, AuthoritiesConstants.BILLING_ADMIN, AuthoritiesConstants.BILLING_SPECIALIST);
        grant(
            PermissionsConstants.VIEW_INVOICE,
            AuthoritiesConstants.BILLING_ADMIN,
            AuthoritiesConstants.BILLING_SPECIALIST,
            AuthoritiesConstants.FINANCE_MANAGER
        );
        grant(PermissionsConstants.UPDATE_INVOICE, AuthoritiesConstants.BILLING_ADMIN, AuthoritiesConstants.BILLING_SPECIALIST);
        grant(PermissionsConstants.DELETE_INVOICE, AuthoritiesConstants.BILLING_ADMIN);
        grant(PermissionsConstants.MANAGE_SUBSCRIPTION_PLANS, AuthoritiesConstants.BILLING_ADMIN);
        grant(PermissionsConstants.MANAGE_FEATURES, AuthoritiesConstants.BILLING_ADMIN);
        grant(PermissionsConstants.MANAGE_COUPONS, AuthoritiesConstants.BILLING_ADMIN, AuthoritiesConstants.BILLING_SPECIALIST);
        grant(PermissionsConstants.PROCESS_PAYMENTS, AuthoritiesConstants.BILLING_SPECIALIST, AuthoritiesConstants.BILLING_ADMIN);
        grant(PermissionsConstants.MANAGE_SUBSCRIPTIONS, AuthoritiesConstants.BILLING_ADMIN, AuthoritiesConstants.BILLING_SPECIALIST);

        // ---- Tenant management (within a tenant) ----
        grant(PermissionsConstants.CREATE_TENANT, AuthoritiesConstants.TENANT_ADMIN);
        grant(PermissionsConstants.VIEW_TENANT, AuthoritiesConstants.TENANT_ADMIN);
        grant(PermissionsConstants.UPDATE_TENANT, AuthoritiesConstants.TENANT_ADMIN);
        grant(PermissionsConstants.MANAGE_ORGANIZATIONS, AuthoritiesConstants.TENANT_ADMIN, AuthoritiesConstants.ORGANIZATION_ADMIN);
        grant(PermissionsConstants.CONFIGURE_TENANT_SETTINGS, AuthoritiesConstants.TENANT_ADMIN);

        // ---- Reporting ----
        grant(PermissionsConstants.VIEW_REPORTS, AuthoritiesConstants.REPORT_VIEWER, AuthoritiesConstants.REPORT_ADMIN);
        grant(PermissionsConstants.CREATE_REPORTS, AuthoritiesConstants.REPORT_ADMIN);
        grant(PermissionsConstants.EXPORT_REPORTS, AuthoritiesConstants.REPORT_VIEWER, AuthoritiesConstants.REPORT_ADMIN);
        grant(PermissionsConstants.SCHEDULE_REPORTS, AuthoritiesConstants.REPORT_ADMIN);
        grant(PermissionsConstants.MANAGE_REPORT_TEMPLATES, AuthoritiesConstants.REPORT_ADMIN);
        grant(PermissionsConstants.VIEW_EXECUTIVE_DASHBOARD, AuthoritiesConstants.EXECUTIVE);

        // ---- Compliance & audit ----
        grant(PermissionsConstants.MANAGE_COMPLIANCE_POLICIES, AuthoritiesConstants.COMPLIANCE_OFFICER);
        grant(PermissionsConstants.VIEW_COMPLIANCE_STATUS, AuthoritiesConstants.COMPLIANCE_OFFICER, AuthoritiesConstants.AUDITOR);
        grant(PermissionsConstants.TRACK_POLICY_VIOLATIONS, AuthoritiesConstants.COMPLIANCE_OFFICER);
        grant(PermissionsConstants.MANAGE_DATA_PRIVACY, AuthoritiesConstants.COMPLIANCE_OFFICER);

        // ---- System administration ----
        grant(PermissionsConstants.SYSTEM_CONFIGURATION);
        grant(PermissionsConstants.AUDIT_LOG_ACCESS, AuthoritiesConstants.AUDITOR, AuthoritiesConstants.COMPLIANCE_OFFICER);
        grant(PermissionsConstants.MANAGE_INTEGRATIONS, AuthoritiesConstants.INTEGRATION_ADMIN);
        grant(PermissionsConstants.MANAGE_API_KEYS, AuthoritiesConstants.INTEGRATION_ADMIN);
        grant(PermissionsConstants.CONFIGURE_WEBHOOKS, AuthoritiesConstants.INTEGRATION_ADMIN);
        grant(PermissionsConstants.MONITOR_SYSTEM_HEALTH, AuthoritiesConstants.IT_SUPPORT);
        grant(PermissionsConstants.USER_SUPPORT_OPERATIONS, AuthoritiesConstants.IT_SUPPORT);
        grant(PermissionsConstants.MANAGE_ROLES);
        grant(PermissionsConstants.ASSIGN_ROLES, AuthoritiesConstants.ORGANIZATION_ADMIN);

        // ---- Invert into role → permissions ----
        Map<String, Set<String>> roleToPermissions = new LinkedHashMap<>();
        ALL_PERMISSIONS = Collections.unmodifiableSet(new LinkedHashSet<>(PERMISSION_TO_ROLES.keySet()));

        for (Map.Entry<String, Set<String>> entry : PERMISSION_TO_ROLES.entrySet()) {
            for (String role : entry.getValue()) {
                roleToPermissions.computeIfAbsent(role, k -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }

        // ADMIN gets everything.
        roleToPermissions.put(AuthoritiesConstants.ADMIN, new LinkedHashSet<>(ALL_PERMISSIONS));

        // Every defined role (and USER) gets dashboard + API access; ensure each appears.
        for (String role : roleToPermissions.keySet()) {
            roleToPermissions.get(role).add(PermissionsConstants.VIEW_DASHBOARD);
            roleToPermissions.get(role).add(PermissionsConstants.ACCESS_API);
        }
        roleToPermissions
            .computeIfAbsent(AuthoritiesConstants.USER, k -> new LinkedHashSet<>())
            .addAll(Set.of(PermissionsConstants.VIEW_DASHBOARD, PermissionsConstants.ACCESS_API));

        // Freeze.
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        roleToPermissions.forEach((role, perms) -> frozen.put(role, Collections.unmodifiableSet(perms)));
        ROLE_TO_PERMISSIONS = Collections.unmodifiableMap(frozen);
    }

    private DefaultRolePermissions() {}

    /** @return the immutable role → permission mapping for a business tenant. */
    public static Map<String, Set<String>> rolePermissions() {
        return ROLE_TO_PERMISSIONS;
    }

    /** @return all role names in the default mapping. */
    public static Set<String> roles() {
        return ROLE_TO_PERMISSIONS.keySet();
    }

    /** @return all distinct permission names referenced by the default mapping. */
    public static Set<String> permissions() {
        return ALL_PERMISSIONS;
    }
}
