package com.humano.security;

/**
 * Constants for permissions in the Humano HR & Payroll Management System.
 * <p>
 * Permissions provide granular access control that can be assigned to roles.
 * Each permission represents a specific action or capability within the system.
 * Permissions are assigned to roles (defined in {@link AuthoritiesConstants}),
 * and users inherit permissions through their assigned roles.
 * </p>
 * <p>
 * Permission naming convention: {@code [ACTION]_[RESOURCE]} or {@code [ACTION]_OWN_[RESOURCE]}
 * </p>
 * <p>
 * Common actions:
 * <ul>
 *     <li>{@code CREATE} - Create new resources</li>
 *     <li>{@code READ/VIEW} - Read or view resources</li>
 *     <li>{@code UPDATE} - Modify existing resources</li>
 *     <li>{@code DELETE} - Remove resources</li>
 *     <li>{@code MANAGE} - Full CRUD operations on resources</li>
 *     <li>{@code APPROVE} - Approval workflow actions</li>
 *     <li>{@code EXPORT} - Export data to external formats</li>
 * </ul>
 * </p>
 *
 * @see AuthoritiesConstants
 * @since 1.0.0
 */
public final class PermissionsConstants {

    // ==================== GENERAL PERMISSIONS ====================

    /**
     * Permission to view the main dashboard.
     * <p>Typically granted to all authenticated users.</p>
     */
    public static final String VIEW_DASHBOARD = "VIEW_DASHBOARD";

    /**
     * Permission to access the REST API.
     * <p>Required for programmatic access to the system.</p>
     */
    public static final String ACCESS_API = "ACCESS_API";

    // ==================== USER MANAGEMENT PERMISSIONS ====================

    /**
     * Permission to create new user accounts.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String CREATE_USER = "CREATE_USER";

    /**
     * Permission to view user account information.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String READ_USER = "READ_USER";

    /**
     * Permission to modify user account information.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String UPDATE_USER = "UPDATE_USER";

    /**
     * Permission to delete or deactivate user accounts.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String DELETE_USER = "DELETE_USER";

    // ==================== HR PERMISSIONS ====================

    /**
     * Permission to create new employee records.
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#HR_SPECIALIST}, {@link AuthoritiesConstants#ONBOARDING_SPECIALIST}</p>
     */
    public static final String CREATE_EMPLOYEE = "CREATE_EMPLOYEE";

    /**
     * Permission to view employee records.
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#HR_SPECIALIST}, {@link AuthoritiesConstants#MANAGER}</p>
     */
    public static final String READ_EMPLOYEE = "READ_EMPLOYEE";

    /**
     * Permission to modify employee records.
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String UPDATE_EMPLOYEE = "UPDATE_EMPLOYEE";

    /**
     * Permission to delete or archive employee records.
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String DELETE_EMPLOYEE = "DELETE_EMPLOYEE";

    /**
     * Permission to manage department structures.
     * <p>Includes creating, updating, and deleting departments.</p>
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#ORGANIZATION_ADMIN}</p>
     */
    public static final String MANAGE_DEPARTMENTS = "MANAGE_DEPARTMENTS";

    /**
     * Permission to manage job positions.
     * <p>Includes creating, updating, and deleting positions.</p>
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_POSITIONS = "MANAGE_POSITIONS";

    /**
     * Permission to manage organizational units and hierarchy.
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#ORGANIZATION_ADMIN}</p>
     */
    public static final String MANAGE_ORGANIZATIONAL_UNITS = "MANAGE_ORGANIZATIONAL_UNITS";

    /**
     * Permission to view team members' information (for managers).
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#DEPARTMENT_HEAD}</p>
     */
    public static final String VIEW_TEAM_MEMBERS = "VIEW_TEAM_MEMBERS";

    // ==================== RECRUITMENT PERMISSIONS ====================

    /**
     * Permission to create job postings.
     * <p>Typically granted to: {@link AuthoritiesConstants#RECRUITER}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String CREATE_JOB_POSTING = "CREATE_JOB_POSTING";

    /**
     * Permission to view job postings.
     * <p>Typically granted to: {@link AuthoritiesConstants#RECRUITER}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String VIEW_JOB_POSTING = "VIEW_JOB_POSTING";

    /**
     * Permission to manage job postings (update, close, archive).
     * <p>Typically granted to: {@link AuthoritiesConstants#RECRUITER}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_JOB_POSTINGS = "MANAGE_JOB_POSTINGS";

    /**
     * Permission to manage candidates and applications.
     * <p>Typically granted to: {@link AuthoritiesConstants#RECRUITER}</p>
     */
    public static final String MANAGE_CANDIDATES = "MANAGE_CANDIDATES";

    /**
     * Permission to schedule and manage interviews.
     * <p>Typically granted to: {@link AuthoritiesConstants#RECRUITER}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String MANAGE_INTERVIEWS = "MANAGE_INTERVIEWS";

    /**
     * Permission to generate and send offer letters.
     * <p>Typically granted to: {@link AuthoritiesConstants#RECRUITER}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String GENERATE_OFFER_LETTERS = "GENERATE_OFFER_LETTERS";

    // ==================== ONBOARDING PERMISSIONS ====================

    /**
     * Permission to manage onboarding checklists and tasks.
     * <p>Typically granted to: {@link AuthoritiesConstants#ONBOARDING_SPECIALIST}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String MANAGE_ONBOARDING = "MANAGE_ONBOARDING";

    /**
     * Permission to view onboarding progress.
     * <p>Typically granted to: {@link AuthoritiesConstants#ONBOARDING_SPECIALIST}, {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#MANAGER}</p>
     */
    public static final String VIEW_ONBOARDING_PROGRESS = "VIEW_ONBOARDING_PROGRESS";

    // ==================== EMPLOYEE SELF-SERVICE PERMISSIONS ====================

    /**
     * Permission to view own employee profile.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_PROFILE = "VIEW_OWN_PROFILE";

    /**
     * Permission to update own employee profile.
     * <p>Limited to non-sensitive fields like contact information.</p>
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String UPDATE_OWN_PROFILE = "UPDATE_OWN_PROFILE";

    /**
     * Permission to view own payslips.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_PAYSLIPS = "VIEW_OWN_PAYSLIPS";

    /**
     * Permission to view own training records and available courses.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_TRAINING = "VIEW_OWN_TRAINING";

    /**
     * Permission to register for available training courses.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String REGISTER_FOR_TRAINING = "REGISTER_FOR_TRAINING";

    /**
     * Permission to view own benefits enrollment.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_BENEFITS = "VIEW_OWN_BENEFITS";

    /**
     * Permission to manage own benefits enrollment (during open enrollment).
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String MANAGE_OWN_BENEFITS = "MANAGE_OWN_BENEFITS";

    /**
     * Permission to view own leave balance and history.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_LEAVE = "VIEW_OWN_LEAVE";

    /**
     * Permission to submit leave requests.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String REQUEST_LEAVE = "REQUEST_LEAVE";

    /**
     * Permission to view own attendance records.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_ATTENDANCE = "VIEW_OWN_ATTENDANCE";

    /**
     * Permission to view own performance reviews and goals.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_PERFORMANCE = "VIEW_OWN_PERFORMANCE";

    /**
     * Permission to view own documents.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_DOCUMENTS = "VIEW_OWN_DOCUMENTS";

    /**
     * Permission to upload own documents (e.g., certifications).
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String UPLOAD_OWN_DOCUMENTS = "UPLOAD_OWN_DOCUMENTS";

    /**
     * Permission to submit expense claims.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String SUBMIT_EXPENSE_CLAIM = "SUBMIT_EXPENSE_CLAIM";

    /**
     * Permission to view own expense claims.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_EXPENSES = "VIEW_OWN_EXPENSES";

    /**
     * Permission to submit timesheets.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String SUBMIT_TIMESHEET = "SUBMIT_TIMESHEET";

    /**
     * Permission to view own timesheets.
     * <p>Typically granted to: {@link AuthoritiesConstants#EMPLOYEE}</p>
     */
    public static final String VIEW_OWN_TIMESHEETS = "VIEW_OWN_TIMESHEETS";

    // ==================== ATTENDANCE & TIME TRACKING PERMISSIONS ====================

    /**
     * Permission to view attendance records (all employees).
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#ATTENDANCE_ADMIN}</p>
     */
    public static final String VIEW_ATTENDANCE = "VIEW_ATTENDANCE";

    /**
     * Permission to manage attendance records and corrections.
     * <p>Typically granted to: {@link AuthoritiesConstants#ATTENDANCE_ADMIN}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String MANAGE_ATTENDANCE = "MANAGE_ATTENDANCE";

    /**
     * Permission to configure attendance policies.
     * <p>Typically granted to: {@link AuthoritiesConstants#ATTENDANCE_ADMIN}</p>
     */
    public static final String CONFIGURE_ATTENDANCE_POLICIES = "CONFIGURE_ATTENDANCE_POLICIES";

    /**
     * Permission to view timesheets (all employees or team).
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#TIMESHEET_APPROVER}</p>
     */
    public static final String VIEW_TIMESHEETS = "VIEW_TIMESHEETS";

    /**
     * Permission to approve or reject timesheets.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#TIMESHEET_APPROVER}</p>
     */
    public static final String APPROVE_TIMESHEETS = "APPROVE_TIMESHEETS";

    /**
     * Permission to manage shifts and schedules.
     * <p>Typically granted to: {@link AuthoritiesConstants#ATTENDANCE_ADMIN}, {@link AuthoritiesConstants#MANAGER}</p>
     */
    public static final String MANAGE_SHIFTS = "MANAGE_SHIFTS";

    /**
     * Permission to approve overtime requests.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#TIMESHEET_APPROVER}</p>
     */
    public static final String APPROVE_OVERTIME = "APPROVE_OVERTIME";

    // ==================== LEAVE MANAGEMENT PERMISSIONS ====================

    /**
     * Permission to view leave requests (all employees or team).
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#LEAVE_ADMIN}, {@link AuthoritiesConstants#MANAGER}</p>
     */
    public static final String VIEW_LEAVE_REQUESTS = "VIEW_LEAVE_REQUESTS";

    /**
     * Permission to approve or reject leave requests.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#LEAVE_APPROVER}</p>
     */
    public static final String APPROVE_LEAVE = "APPROVE_LEAVE";

    /**
     * Permission to configure leave types and policies.
     * <p>Typically granted to: {@link AuthoritiesConstants#LEAVE_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String CONFIGURE_LEAVE_POLICIES = "CONFIGURE_LEAVE_POLICIES";

    /**
     * Permission to adjust leave balances manually.
     * <p>Typically granted to: {@link AuthoritiesConstants#LEAVE_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String ADJUST_LEAVE_BALANCE = "ADJUST_LEAVE_BALANCE";

    /**
     * Permission to manage holiday calendars.
     * <p>Typically granted to: {@link AuthoritiesConstants#LEAVE_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_HOLIDAYS = "MANAGE_HOLIDAYS";

    // ==================== TRAINING & DEVELOPMENT PERMISSIONS ====================

    /**
     * Permission to create training programs and courses.
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_ADMIN}</p>
     */
    public static final String CREATE_TRAINING = "CREATE_TRAINING";

    /**
     * Permission to view all training programs.
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_ADMIN}, {@link AuthoritiesConstants#TRAINING_COORDINATOR}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String VIEW_TRAINING = "VIEW_TRAINING";

    /**
     * Permission to manage training programs (update, archive).
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_ADMIN}</p>
     */
    public static final String MANAGE_TRAINING = "MANAGE_TRAINING";

    /**
     * Permission to schedule training sessions.
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_COORDINATOR}, {@link AuthoritiesConstants#TRAINING_ADMIN}</p>
     */
    public static final String SCHEDULE_TRAINING = "SCHEDULE_TRAINING";

    /**
     * Permission to manage training enrollments.
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_COORDINATOR}</p>
     */
    public static final String MANAGE_TRAINING_ENROLLMENTS = "MANAGE_TRAINING_ENROLLMENTS";

    /**
     * Permission to track and manage certifications.
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_ADMIN}, {@link AuthoritiesConstants#HR_SPECIALIST}</p>
     */
    public static final String MANAGE_CERTIFICATIONS = "MANAGE_CERTIFICATIONS";

    /**
     * Permission to manage training budgets.
     * <p>Typically granted to: {@link AuthoritiesConstants#TRAINING_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_TRAINING_BUDGET = "MANAGE_TRAINING_BUDGET";

    // ==================== PERFORMANCE MANAGEMENT PERMISSIONS ====================

    /**
     * Permission to configure performance review cycles.
     * <p>Typically granted to: {@link AuthoritiesConstants#PERFORMANCE_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String CONFIGURE_PERFORMANCE_CYCLES = "CONFIGURE_PERFORMANCE_CYCLES";

    /**
     * Permission to manage goal and competency frameworks.
     * <p>Typically granted to: {@link AuthoritiesConstants#PERFORMANCE_ADMIN}</p>
     */
    public static final String MANAGE_PERFORMANCE_FRAMEWORKS = "MANAGE_PERFORMANCE_FRAMEWORKS";

    /**
     * Permission to submit performance reviews.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#PERFORMANCE_REVIEWER}</p>
     */
    public static final String SUBMIT_PERFORMANCE_REVIEW = "SUBMIT_PERFORMANCE_REVIEW";

    /**
     * Permission to view performance reviews (all employees).
     * <p>Typically granted to: {@link AuthoritiesConstants#HR_MANAGER}, {@link AuthoritiesConstants#PERFORMANCE_ADMIN}</p>
     */
    public static final String VIEW_PERFORMANCE_REVIEWS = "VIEW_PERFORMANCE_REVIEWS";

    /**
     * Permission to view team performance reviews.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#DEPARTMENT_HEAD}</p>
     */
    public static final String VIEW_TEAM_PERFORMANCE = "VIEW_TEAM_PERFORMANCE";

    /**
     * Permission to manage calibration sessions.
     * <p>Typically granted to: {@link AuthoritiesConstants#PERFORMANCE_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_CALIBRATION = "MANAGE_CALIBRATION";

    /**
     * Permission to set and track employee goals.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#PERFORMANCE_REVIEWER}</p>
     */
    public static final String MANAGE_GOALS = "MANAGE_GOALS";

    // ==================== BENEFITS ADMINISTRATION PERMISSIONS ====================

    /**
     * Permission to configure benefit plans.
     * <p>Typically granted to: {@link AuthoritiesConstants#BENEFITS_ADMIN}</p>
     */
    public static final String CONFIGURE_BENEFIT_PLANS = "CONFIGURE_BENEFIT_PLANS";

    /**
     * Permission to manage health insurance programs.
     * <p>Typically granted to: {@link AuthoritiesConstants#BENEFITS_ADMIN}</p>
     */
    public static final String MANAGE_HEALTH_INSURANCE = "MANAGE_HEALTH_INSURANCE";

    /**
     * Permission to manage open enrollment periods.
     * <p>Typically granted to: {@link AuthoritiesConstants#BENEFITS_ADMIN}</p>
     */
    public static final String MANAGE_OPEN_ENROLLMENT = "MANAGE_OPEN_ENROLLMENT";

    /**
     * Permission to process benefit enrollments.
     * <p>Typically granted to: {@link AuthoritiesConstants#BENEFITS_SPECIALIST}, {@link AuthoritiesConstants#BENEFITS_ADMIN}</p>
     */
    public static final String PROCESS_BENEFIT_ENROLLMENTS = "PROCESS_BENEFIT_ENROLLMENTS";

    /**
     * Permission to view all employee benefits.
     * <p>Typically granted to: {@link AuthoritiesConstants#BENEFITS_ADMIN}, {@link AuthoritiesConstants#BENEFITS_SPECIALIST}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String VIEW_EMPLOYEE_BENEFITS = "VIEW_EMPLOYEE_BENEFITS";

    /**
     * Permission to manage benefit vendors.
     * <p>Typically granted to: {@link AuthoritiesConstants#BENEFITS_ADMIN}</p>
     */
    public static final String MANAGE_BENEFIT_VENDORS = "MANAGE_BENEFIT_VENDORS";

    // ==================== EXPENSE MANAGEMENT PERMISSIONS ====================

    /**
     * Permission to view expense claims (all or team).
     * <p>Typically granted to: {@link AuthoritiesConstants#EXPENSE_ADMIN}, {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String VIEW_EXPENSE_CLAIMS = "VIEW_EXPENSE_CLAIMS";

    /**
     * Permission to approve or reject expense claims.
     * <p>Typically granted to: {@link AuthoritiesConstants#MANAGER}, {@link AuthoritiesConstants#EXPENSE_APPROVER}</p>
     */
    public static final String APPROVE_EXPENSE_CLAIMS = "APPROVE_EXPENSE_CLAIMS";

    /**
     * Permission to configure expense policies and categories.
     * <p>Typically granted to: {@link AuthoritiesConstants#EXPENSE_ADMIN}, {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String CONFIGURE_EXPENSE_POLICIES = "CONFIGURE_EXPENSE_POLICIES";

    /**
     * Permission to manage corporate cards.
     * <p>Typically granted to: {@link AuthoritiesConstants#EXPENSE_ADMIN}, {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String MANAGE_CORPORATE_CARDS = "MANAGE_CORPORATE_CARDS";

    // ==================== PAYROLL PERMISSIONS ====================

    /**
     * Permission to create new payroll runs.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#PAYROLL_SPECIALIST}</p>
     */
    public static final String CREATE_PAYROLL_RUN = "CREATE_PAYROLL_RUN";

    /**
     * Permission to view payroll runs and details.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#PAYROLL_SPECIALIST}, {@link AuthoritiesConstants#PAYROLL_VIEWER}</p>
     */
    public static final String VIEW_PAYROLL_RUN = "VIEW_PAYROLL_RUN";

    /**
     * Permission to approve payroll runs for processing.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String APPROVE_PAYROLL = "APPROVE_PAYROLL";

    /**
     * Permission to process and finalize payroll.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}</p>
     */
    public static final String PROCESS_PAYROLL = "PROCESS_PAYROLL";

    /**
     * Permission to manage pay components (earnings types).
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}</p>
     */
    public static final String MANAGE_PAY_COMPONENTS = "MANAGE_PAY_COMPONENTS";

    /**
     * Permission to manage deductions.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}</p>
     */
    public static final String MANAGE_DEDUCTIONS = "MANAGE_DEDUCTIONS";

    /**
     * Permission to manage employee benefits in payroll.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#BENEFITS_ADMIN}</p>
     */
    public static final String MANAGE_BENEFITS = "MANAGE_BENEFITS";

    /**
     * Permission to manage tax brackets and tax configurations.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}</p>
     */
    public static final String MANAGE_TAX_BRACKETS = "MANAGE_TAX_BRACKETS";

    /**
     * Permission to view all employee payslips.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#PAYROLL_SPECIALIST}, {@link AuthoritiesConstants#PAYROLL_VIEWER}</p>
     */
    public static final String VIEW_PAYSLIPS = "VIEW_PAYSLIPS";

    /**
     * Permission to generate and distribute payslips.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#PAYROLL_SPECIALIST}</p>
     */
    public static final String GENERATE_PAYSLIPS = "GENERATE_PAYSLIPS";

    /**
     * Permission to manage compensation structures.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_COMPENSATION = "MANAGE_COMPENSATION";

    /**
     * Permission to manage bonuses.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}, {@link AuthoritiesConstants#HR_MANAGER}</p>
     */
    public static final String MANAGE_BONUSES = "MANAGE_BONUSES";

    /**
     * Permission to configure payroll calendars.
     * <p>Typically granted to: {@link AuthoritiesConstants#PAYROLL_ADMIN}</p>
     */
    public static final String CONFIGURE_PAYROLL_CALENDAR = "CONFIGURE_PAYROLL_CALENDAR";

    // ==================== FINANCE PERMISSIONS ====================

    /**
     * Permission to manage budgets.
     * <p>Typically granted to: {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String MANAGE_BUDGETS = "MANAGE_BUDGETS";

    /**
     * Permission to view financial reports.
     * <p>Typically granted to: {@link AuthoritiesConstants#FINANCE_MANAGER}, {@link AuthoritiesConstants#FINANCE_SPECIALIST}, {@link AuthoritiesConstants#EXECUTIVE}</p>
     */
    public static final String VIEW_FINANCIAL_REPORTS = "VIEW_FINANCIAL_REPORTS";

    /**
     * Permission to manage exchange rates.
     * <p>Typically granted to: {@link AuthoritiesConstants#FINANCE_MANAGER}, {@link AuthoritiesConstants#PAYROLL_ADMIN}</p>
     */
    public static final String MANAGE_EXCHANGE_RATES = "MANAGE_EXCHANGE_RATES";

    /**
     * Permission to manage cost centers.
     * <p>Typically granted to: {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String MANAGE_COST_CENTERS = "MANAGE_COST_CENTERS";

    // ==================== BILLING PERMISSIONS ====================

    /**
     * Permission to create invoices.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}, {@link AuthoritiesConstants#BILLING_SPECIALIST}</p>
     */
    public static final String CREATE_INVOICE = "CREATE_INVOICE";

    /**
     * Permission to view invoices.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}, {@link AuthoritiesConstants#BILLING_SPECIALIST}, {@link AuthoritiesConstants#FINANCE_MANAGER}</p>
     */
    public static final String VIEW_INVOICE = "VIEW_INVOICE";

    /**
     * Permission to update invoices.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}, {@link AuthoritiesConstants#BILLING_SPECIALIST}</p>
     */
    public static final String UPDATE_INVOICE = "UPDATE_INVOICE";

    /**
     * Permission to delete or void invoices.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}</p>
     */
    public static final String DELETE_INVOICE = "DELETE_INVOICE";

    /**
     * Permission to manage subscription plans.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}</p>
     */
    public static final String MANAGE_SUBSCRIPTION_PLANS = "MANAGE_SUBSCRIPTION_PLANS";

    /**
     * Permission to manage features for subscription tiers.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}</p>
     */
    public static final String MANAGE_FEATURES = "MANAGE_FEATURES";

    /**
     * Permission to manage coupons and discounts.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}, {@link AuthoritiesConstants#BILLING_SPECIALIST}</p>
     */
    public static final String MANAGE_COUPONS = "MANAGE_COUPONS";

    /**
     * Permission to process payments.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_SPECIALIST}, {@link AuthoritiesConstants#BILLING_ADMIN}</p>
     */
    public static final String PROCESS_PAYMENTS = "PROCESS_PAYMENTS";

    /**
     * Permission to manage subscriptions.
     * <p>Typically granted to: {@link AuthoritiesConstants#BILLING_ADMIN}, {@link AuthoritiesConstants#BILLING_SPECIALIST}</p>
     */
    public static final String MANAGE_SUBSCRIPTIONS = "MANAGE_SUBSCRIPTIONS";

    // ==================== TENANT MANAGEMENT PERMISSIONS ====================

    /**
     * Permission to create new tenants.
     * <p>Typically granted to: {@link AuthoritiesConstants#TENANT_ADMIN}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String CREATE_TENANT = "CREATE_TENANT";

    /**
     * Permission to view tenant information.
     * <p>Typically granted to: {@link AuthoritiesConstants#TENANT_ADMIN}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String VIEW_TENANT = "VIEW_TENANT";

    /**
     * Permission to update tenant settings.
     * <p>Typically granted to: {@link AuthoritiesConstants#TENANT_ADMIN}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String UPDATE_TENANT = "UPDATE_TENANT";

    /**
     * Permission to delete or deactivate tenants.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String DELETE_TENANT = "DELETE_TENANT";

    /**
     * Permission to manage organizations within a tenant.
     * <p>Typically granted to: {@link AuthoritiesConstants#TENANT_ADMIN}, {@link AuthoritiesConstants#ORGANIZATION_ADMIN}</p>
     */
    public static final String MANAGE_ORGANIZATIONS = "MANAGE_ORGANIZATIONS";

    /**
     * Permission to configure tenant-level settings.
     * <p>Typically granted to: {@link AuthoritiesConstants#TENANT_ADMIN}</p>
     */
    public static final String CONFIGURE_TENANT_SETTINGS = "CONFIGURE_TENANT_SETTINGS";

    // ==================== REPORTING PERMISSIONS ====================

    /**
     * Permission to view pre-built reports.
     * <p>Typically granted to: {@link AuthoritiesConstants#REPORT_VIEWER}, {@link AuthoritiesConstants#REPORT_ADMIN}</p>
     */
    public static final String VIEW_REPORTS = "VIEW_REPORTS";

    /**
     * Permission to create custom reports.
     * <p>Typically granted to: {@link AuthoritiesConstants#REPORT_ADMIN}</p>
     */
    public static final String CREATE_REPORTS = "CREATE_REPORTS";

    /**
     * Permission to export reports to various formats.
     * <p>Typically granted to: {@link AuthoritiesConstants#REPORT_VIEWER}, {@link AuthoritiesConstants#REPORT_ADMIN}</p>
     */
    public static final String EXPORT_REPORTS = "EXPORT_REPORTS";

    /**
     * Permission to schedule automated report generation and distribution.
     * <p>Typically granted to: {@link AuthoritiesConstants#REPORT_ADMIN}</p>
     */
    public static final String SCHEDULE_REPORTS = "SCHEDULE_REPORTS";

    /**
     * Permission to manage report templates.
     * <p>Typically granted to: {@link AuthoritiesConstants#REPORT_ADMIN}</p>
     */
    public static final String MANAGE_REPORT_TEMPLATES = "MANAGE_REPORT_TEMPLATES";

    /**
     * Permission to access executive dashboards.
     * <p>Typically granted to: {@link AuthoritiesConstants#EXECUTIVE}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String VIEW_EXECUTIVE_DASHBOARD = "VIEW_EXECUTIVE_DASHBOARD";

    // ==================== COMPLIANCE & AUDIT PERMISSIONS ====================

    /**
     * Permission to manage compliance policies.
     * <p>Typically granted to: {@link AuthoritiesConstants#COMPLIANCE_OFFICER}</p>
     */
    public static final String MANAGE_COMPLIANCE_POLICIES = "MANAGE_COMPLIANCE_POLICIES";

    /**
     * Permission to view compliance status and reports.
     * <p>Typically granted to: {@link AuthoritiesConstants#COMPLIANCE_OFFICER}, {@link AuthoritiesConstants#AUDITOR}</p>
     */
    public static final String VIEW_COMPLIANCE_STATUS = "VIEW_COMPLIANCE_STATUS";

    /**
     * Permission to track policy violations.
     * <p>Typically granted to: {@link AuthoritiesConstants#COMPLIANCE_OFFICER}</p>
     */
    public static final String TRACK_POLICY_VIOLATIONS = "TRACK_POLICY_VIOLATIONS";

    /**
     * Permission to manage data privacy settings.
     * <p>Typically granted to: {@link AuthoritiesConstants#COMPLIANCE_OFFICER}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String MANAGE_DATA_PRIVACY = "MANAGE_DATA_PRIVACY";

    // ==================== SYSTEM ADMINISTRATION PERMISSIONS ====================

    /**
     * Permission to configure system settings.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String SYSTEM_CONFIGURATION = "SYSTEM_CONFIGURATION";

    /**
     * Permission to access and view audit logs.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#AUDITOR}, {@link AuthoritiesConstants#COMPLIANCE_OFFICER}</p>
     */
    public static final String AUDIT_LOG_ACCESS = "AUDIT_LOG_ACCESS";

    /**
     * Permission to manage third-party integrations.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#INTEGRATION_ADMIN}</p>
     */
    public static final String MANAGE_INTEGRATIONS = "MANAGE_INTEGRATIONS";

    /**
     * Permission to manage API keys and webhooks.
     * <p>Typically granted to: {@link AuthoritiesConstants#INTEGRATION_ADMIN}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String MANAGE_API_KEYS = "MANAGE_API_KEYS";

    /**
     * Permission to configure webhooks.
     * <p>Typically granted to: {@link AuthoritiesConstants#INTEGRATION_ADMIN}</p>
     */
    public static final String CONFIGURE_WEBHOOKS = "CONFIGURE_WEBHOOKS";

    /**
     * Permission to monitor system health and status.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#IT_SUPPORT}</p>
     */
    public static final String MONITOR_SYSTEM_HEALTH = "MONITOR_SYSTEM_HEALTH";

    /**
     * Permission to perform user support operations (password reset, account unlock).
     * <p>Typically granted to: {@link AuthoritiesConstants#IT_SUPPORT}, {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String USER_SUPPORT_OPERATIONS = "USER_SUPPORT_OPERATIONS";

    /**
     * Permission to manage roles and their permissions.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}</p>
     */
    public static final String MANAGE_ROLES = "MANAGE_ROLES";

    /**
     * Permission to assign roles to users.
     * <p>Typically granted to: {@link AuthoritiesConstants#ADMIN}, {@link AuthoritiesConstants#ORGANIZATION_ADMIN}</p>
     */
    public static final String ASSIGN_ROLES = "ASSIGN_ROLES";

    private PermissionsConstants() {}
}
