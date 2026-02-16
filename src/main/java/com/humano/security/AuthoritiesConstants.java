package com.humano.security;

/**
 * Constants for Spring Security authorities.
 * <p>
 * This class defines all the roles available in the Humano HR & Payroll Management System.
 * Roles are organized by functional domain and follow a hierarchical structure where
 * higher-level roles (e.g., ADMIN, MANAGER) typically inherit permissions from lower-level roles.
 * </p>
 * <p>
 * Role naming convention: {@code ROLE_[DOMAIN]_[LEVEL]}
 * </p>
 *
 * @since 1.0.0
 */
public final class AuthoritiesConstants {

    // ==================== SYSTEM ROLES ====================

    /**
     * Super administrator role with unrestricted access to all system features.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Full system configuration</li>
     *     <li>User and role management</li>
     *     <li>All domain operations</li>
     *     <li>Audit log access</li>
     * </ul>
     * </p>
     */
    public static final String ADMIN = "ROLE_ADMIN";

    /**
     * Basic authenticated user role.
     * <p>
     * Minimum role required for authenticated access to the system.
     * Typically combined with other domain-specific roles.
     * </p>
     */
    public static final String USER = "ROLE_USER";

    /**
     * Role for unauthenticated users.
     * <p>
     * Used for public endpoints that don't require authentication.
     * </p>
     */
    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    // ==================== HR ROLES ====================

    /**
     * HR department manager with full HR operations access.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Employee lifecycle management (hire, transfer, terminate)</li>
     *     <li>Department and position management</li>
     *     <li>Leave policy configuration</li>
     *     <li>HR reporting and analytics</li>
     *     <li>Approval of HR requests</li>
     * </ul>
     * </p>
     */
    public static final String HR_MANAGER = "ROLE_HR_MANAGER";

    /**
     * HR specialist for day-to-day HR operations.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Employee record management</li>
     *     <li>Document processing</li>
     *     <li>Leave request processing</li>
     *     <li>Basic HR reporting</li>
     * </ul>
     * </p>
     */
    public static final String HR_SPECIALIST = "ROLE_HR_SPECIALIST";

    // ==================== RECRUITMENT & ONBOARDING ROLES ====================

    /**
     * Recruiter role for talent acquisition activities.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Job posting management</li>
     *     <li>Candidate tracking and evaluation</li>
     *     <li>Interview scheduling</li>
     *     <li>Offer letter generation</li>
     *     <li>Recruitment pipeline reporting</li>
     * </ul>
     * </p>
     */
    public static final String RECRUITER = "ROLE_RECRUITER";

    /**
     * Onboarding specialist for new employee integration.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Onboarding checklist management</li>
     *     <li>New hire documentation</li>
     *     <li>Equipment and access provisioning requests</li>
     *     <li>Orientation scheduling</li>
     *     <li>Onboarding progress tracking</li>
     * </ul>
     * </p>
     */
    public static final String ONBOARDING_SPECIALIST = "ROLE_ONBOARDING_SPECIALIST";

    // ==================== PAYROLL ROLES ====================

    /**
     * Payroll administrator with full payroll system access.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Payroll configuration and policies</li>
     *     <li>Pay component and deduction setup</li>
     *     <li>Tax bracket management</li>
     *     <li>Payroll run approval and finalization</li>
     *     <li>Compensation structure management</li>
     * </ul>
     * </p>
     */
    public static final String PAYROLL_ADMIN = "ROLE_PAYROLL_ADMIN";

    /**
     * Payroll specialist for payroll processing operations.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Payroll data entry and validation</li>
     *     <li>Payroll run execution</li>
     *     <li>Payslip generation</li>
     *     <li>Payroll adjustments and corrections</li>
     *     <li>Basic payroll reporting</li>
     * </ul>
     * </p>
     */
    public static final String PAYROLL_SPECIALIST = "ROLE_PAYROLL_SPECIALIST";

    /**
     * Read-only access to payroll information.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>View payroll runs and history</li>
     *     <li>View payslips</li>
     *     <li>View payroll reports</li>
     * </ul>
     * </p>
     */
    public static final String PAYROLL_VIEWER = "ROLE_PAYROLL_VIEWER";

    // ==================== FINANCE ROLES ====================

    /**
     * Finance department manager with full financial oversight.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Budget management and approval</li>
     *     <li>Financial reporting and analytics</li>
     *     <li>Exchange rate management</li>
     *     <li>Cost center management</li>
     *     <li>Financial policy configuration</li>
     * </ul>
     * </p>
     */
    public static final String FINANCE_MANAGER = "ROLE_FINANCE_MANAGER";

    /**
     * Finance specialist for financial operations.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Financial data entry</li>
     *     <li>Invoice processing</li>
     *     <li>Expense reconciliation</li>
     *     <li>Basic financial reporting</li>
     * </ul>
     * </p>
     */
    public static final String FINANCE_SPECIALIST = "ROLE_FINANCE_SPECIALIST";

    // ==================== BILLING ROLES ====================

    /**
     * Billing administrator for subscription and invoice management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Subscription plan configuration</li>
     *     <li>Feature management</li>
     *     <li>Coupon and discount management</li>
     *     <li>Payment gateway configuration</li>
     *     <li>Billing policy setup</li>
     * </ul>
     * </p>
     */
    public static final String BILLING_ADMIN = "ROLE_BILLING_ADMIN";

    /**
     * Billing specialist for billing operations.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Invoice generation and management</li>
     *     <li>Payment processing</li>
     *     <li>Subscription management</li>
     *     <li>Customer billing inquiries</li>
     *     <li>Billing reports</li>
     * </ul>
     * </p>
     */
    public static final String BILLING_SPECIALIST = "ROLE_BILLING_SPECIALIST";

    // ==================== TENANT MANAGEMENT ROLES ====================

    /**
     * Tenant administrator for multi-tenant system management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Tenant creation and configuration</li>
     *     <li>Tenant data isolation management</li>
     *     <li>Cross-tenant reporting</li>
     *     <li>Tenant resource allocation</li>
     *     <li>Tenant-level feature toggling</li>
     * </ul>
     * </p>
     */
    public static final String TENANT_ADMIN = "ROLE_TENANT_ADMIN";

    /**
     * Organization administrator within a tenant.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Organization settings management</li>
     *     <li>User management within organization</li>
     *     <li>Organization structure configuration</li>
     *     <li>Role assignment within organization</li>
     *     <li>Organization-level reporting</li>
     * </ul>
     * </p>
     */
    public static final String ORGANIZATION_ADMIN = "ROLE_ORGANIZATION_ADMIN";

    // ==================== EMPLOYEE & MANAGER ROLES ====================

    /**
     * Regular employee with self-service access.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>View and update own profile</li>
     *     <li>View own payslips and compensation</li>
     *     <li>Submit leave requests</li>
     *     <li>Submit expense claims</li>
     *     <li>View own attendance records</li>
     *     <li>Access training and development resources</li>
     *     <li>View own performance reviews</li>
     * </ul>
     * </p>
     */
    public static final String EMPLOYEE = "ROLE_EMPLOYEE";

    /**
     * Team or department manager with supervisory access.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>View team members' profiles</li>
     *     <li>Approve/reject leave requests</li>
     *     <li>Approve/reject expense claims</li>
     *     <li>View team attendance and timesheets</li>
     *     <li>Conduct performance reviews</li>
     *     <li>Team-level reporting</li>
     * </ul>
     * </p>
     */
    public static final String MANAGER = "ROLE_MANAGER";

    /**
     * Department head with full departmental oversight.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>All manager permissions</li>
     *     <li>Department budget management</li>
     *     <li>Headcount planning</li>
     *     <li>Department-wide reporting</li>
     *     <li>Salary review recommendations</li>
     *     <li>Department structure management</li>
     * </ul>
     * </p>
     */
    public static final String DEPARTMENT_HEAD = "ROLE_DEPARTMENT_HEAD";

    /**
     * Executive role for C-level and senior leadership.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Executive dashboards and KPIs</li>
     *     <li>Organization-wide analytics</li>
     *     <li>Strategic workforce planning reports</li>
     *     <li>Compensation benchmarking</li>
     *     <li>High-level financial overviews</li>
     * </ul>
     * </p>
     */
    public static final String EXECUTIVE = "ROLE_EXECUTIVE";

    // ==================== TIME & ATTENDANCE ROLES ====================

    /**
     * Attendance administrator for time tracking configuration.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Attendance policy configuration</li>
     *     <li>Shift and schedule management</li>
     *     <li>Overtime rules configuration</li>
     *     <li>Time clock device management</li>
     *     <li>Attendance reports administration</li>
     * </ul>
     * </p>
     */
    public static final String ATTENDANCE_ADMIN = "ROLE_ATTENDANCE_ADMIN";

    /**
     * Timesheet approver for time entry validation.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Review and approve timesheets</li>
     *     <li>Approve overtime requests</li>
     *     <li>Attendance correction approvals</li>
     *     <li>View team attendance reports</li>
     * </ul>
     * </p>
     */
    public static final String TIMESHEET_APPROVER = "ROLE_TIMESHEET_APPROVER";

    // ==================== LEAVE MANAGEMENT ROLES ====================

    /**
     * Leave approver for leave request processing.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Approve/reject leave requests</li>
     *     <li>View team leave calendar</li>
     *     <li>View leave balances</li>
     *     <li>Leave conflict resolution</li>
     * </ul>
     * </p>
     */
    public static final String LEAVE_APPROVER = "ROLE_LEAVE_APPROVER";

    /**
     * Leave administrator for leave policy management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Leave type configuration</li>
     *     <li>Leave policy setup</li>
     *     <li>Leave balance adjustments</li>
     *     <li>Holiday calendar management</li>
     *     <li>Leave accrual rules configuration</li>
     *     <li>Leave reports administration</li>
     * </ul>
     * </p>
     */
    public static final String LEAVE_ADMIN = "ROLE_LEAVE_ADMIN";

    // ==================== TRAINING & DEVELOPMENT ROLES ====================

    /**
     * Training administrator for learning management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Training program creation and management</li>
     *     <li>Course catalog management</li>
     *     <li>Training budget management</li>
     *     <li>Certification tracking</li>
     *     <li>Training effectiveness reporting</li>
     *     <li>External training vendor management</li>
     * </ul>
     * </p>
     */
    public static final String TRAINING_ADMIN = "ROLE_TRAINING_ADMIN";

    /**
     * Training coordinator for session scheduling.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Training session scheduling</li>
     *     <li>Participant enrollment management</li>
     *     <li>Training resource booking</li>
     *     <li>Training attendance tracking</li>
     *     <li>Training feedback collection</li>
     * </ul>
     * </p>
     */
    public static final String TRAINING_COORDINATOR = "ROLE_TRAINING_COORDINATOR";

    // ==================== PERFORMANCE MANAGEMENT ROLES ====================

    /**
     * Performance administrator for review cycle management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Performance review cycle configuration</li>
     *     <li>Goal and competency framework setup</li>
     *     <li>Review template management</li>
     *     <li>Calibration session management</li>
     *     <li>Performance analytics and reporting</li>
     * </ul>
     * </p>
     */
    public static final String PERFORMANCE_ADMIN = "ROLE_PERFORMANCE_ADMIN";

    /**
     * Performance reviewer for conducting evaluations.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Submit performance reviews</li>
     *     <li>Set and track employee goals</li>
     *     <li>Provide feedback and ratings</li>
     *     <li>View historical performance data</li>
     *     <li>Participate in calibration sessions</li>
     * </ul>
     * </p>
     */
    public static final String PERFORMANCE_REVIEWER = "ROLE_PERFORMANCE_REVIEWER";

    // ==================== BENEFITS ADMINISTRATION ROLES ====================

    /**
     * Benefits administrator for benefit program management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Benefit plan configuration</li>
     *     <li>Health insurance program management</li>
     *     <li>Open enrollment management</li>
     *     <li>Benefit vendor management</li>
     *     <li>Benefits cost analysis</li>
     *     <li>Compliance reporting</li>
     * </ul>
     * </p>
     */
    public static final String BENEFITS_ADMIN = "ROLE_BENEFITS_ADMIN";

    /**
     * Benefits specialist for enrollment processing.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Process benefit enrollments</li>
     *     <li>Handle benefit claims</li>
     *     <li>Manage beneficiary information</li>
     *     <li>Answer employee benefit inquiries</li>
     *     <li>Generate benefits statements</li>
     * </ul>
     * </p>
     */
    public static final String BENEFITS_SPECIALIST = "ROLE_BENEFITS_SPECIALIST";

    // ==================== EXPENSE MANAGEMENT ROLES ====================

    /**
     * Expense approver for expense claim processing.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Approve/reject expense claims</li>
     *     <li>View team expense reports</li>
     *     <li>Request expense documentation</li>
     *     <li>Expense limit exception handling</li>
     * </ul>
     * </p>
     */
    public static final String EXPENSE_APPROVER = "ROLE_EXPENSE_APPROVER";

    /**
     * Expense administrator for expense policy management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Expense category configuration</li>
     *     <li>Expense policy setup</li>
     *     <li>Spending limit configuration</li>
     *     <li>Receipt requirements management</li>
     *     <li>Expense analytics and reporting</li>
     *     <li>Corporate card management</li>
     * </ul>
     * </p>
     */
    public static final String EXPENSE_ADMIN = "ROLE_EXPENSE_ADMIN";

    // ==================== REPORTING ROLES ====================

    /**
     * Report viewer with read-only access to reports.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>View pre-built reports</li>
     *     <li>Export reports to various formats</li>
     *     <li>Subscribe to scheduled reports</li>
     * </ul>
     * </p>
     */
    public static final String REPORT_VIEWER = "ROLE_REPORT_VIEWER";

    /**
     * Report administrator with full reporting capabilities.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Create custom reports</li>
     *     <li>Configure report templates</li>
     *     <li>Schedule automated reports</li>
     *     <li>Manage report distribution</li>
     *     <li>Access all report data sources</li>
     * </ul>
     * </p>
     */
    public static final String REPORT_ADMIN = "ROLE_REPORT_ADMIN";

    // ==================== COMPLIANCE & AUDIT ROLES ====================

    /**
     * Compliance officer for regulatory compliance management.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>Compliance policy management</li>
     *     <li>Regulatory reporting</li>
     *     <li>Compliance audit preparation</li>
     *     <li>Policy violation tracking</li>
     *     <li>Compliance training oversight</li>
     *     <li>Data privacy management</li>
     * </ul>
     * </p>
     */
    public static final String COMPLIANCE_OFFICER = "ROLE_COMPLIANCE_OFFICER";

    /**
     * Auditor with read-only access for audit purposes.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>View audit logs</li>
     *     <li>Access historical records</li>
     *     <li>Generate audit reports</li>
     *     <li>Review data change history</li>
     *     <li>Access compliance documentation</li>
     * </ul>
     * </p>
     * <p>
     * Note: This role is read-only and cannot modify any data.
     * </p>
     */
    public static final String AUDITOR = "ROLE_AUDITOR";

    // ==================== IT & SYSTEM SUPPORT ROLES ====================

    /**
     * IT support for technical assistance.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>User account troubleshooting</li>
     *     <li>Password reset assistance</li>
     *     <li>Access provisioning support</li>
     *     <li>System status monitoring</li>
     *     <li>Basic configuration support</li>
     * </ul>
     * </p>
     */
    public static final String IT_SUPPORT = "ROLE_IT_SUPPORT";

    /**
     * Integration administrator for third-party system connections.
     * <p>
     * Permissions include:
     * <ul>
     *     <li>API integration management</li>
     *     <li>Data synchronization configuration</li>
     *     <li>Integration monitoring and logging</li>
     *     <li>Webhook configuration</li>
     *     <li>External system credential management</li>
     * </ul>
     * </p>
     */
    public static final String INTEGRATION_ADMIN = "ROLE_INTEGRATION_ADMIN";

    private AuthoritiesConstants() {}
}
