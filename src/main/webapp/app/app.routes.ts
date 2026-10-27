import { Routes } from '@angular/router';

import { Permission } from 'app/config/permission.constants';
import { dashboardGuard } from 'app/core/auth/dashboard.guard';
import { platformGuard, workspaceGuard } from 'app/core/auth/platform.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';

const placeholder = () => import('./layouts/placeholder-page.component');

/**
 * Build a placeholder route. `titleKey` drives both the browser title (via
 * `AppPageTitleStrategy`) and the in-page header. `permissions` (any-of), when
 * set, gate the route with the auth guard against the account's effective
 * permissions — mirroring the backend `@RequirePermission` checks.
 */
const page = (path: string, titleKey: string, permissions?: string[]) => ({
  path,
  title: titleKey,
  loadComponent: placeholder,
  data: { titleKey, ...(permissions ? { permissions } : {}) },
  ...(permissions ? { canActivate: [UserRouteAccessService] } : {}),
});

// `/dev/ui` is the design-system gallery / verification harness (outside the shell).
// The authenticated app lives under the shell layout; feature routes currently
// resolve to a shared placeholder until each persona surface lands in Phase 7.
const routes: Routes = [
  {
    path: 'dev/ui',
    title: 'UI gallery',
    loadComponent: () => import('./dev/ui-gallery.component'),
  },
  // Public marketing landing — matches exact `/` only; shell children still prefix-match below.
  {
    path: '',
    pathMatch: 'full',
    title: 'humano.landing.title',
    loadComponent: () => import('./features/marketing/landing-page.component'),
  },
  // Public auth screens — split-screen brand chrome shared via AuthLayoutComponent.
  {
    path: '',
    loadComponent: () => import('./layouts/auth-layout.component'),
    children: [
      {
        path: 'login',
        title: 'login.title',
        loadComponent: () => import('./login/login.component'),
      },
      {
        path: 'activate',
        title: 'activate.title',
        loadComponent: () => import('./login/activate.component'),
      },
      {
        path: 'account/reset/request',
        title: 'reset.request.title',
        loadComponent: () => import('./login/password-reset-request.component'),
      },
      {
        path: 'account/reset/finish',
        title: 'reset.finish.title',
        loadComponent: () => import('./login/password-reset-finish.component'),
      },
      {
        path: 'signup',
        title: 'register.title',
        loadComponent: () => import('./login/signup.component'),
      },
      {
        path: 'help/payroll-setup',
        title: 'register.payrollGuide.title',
        loadComponent: () => import('./features/marketing/payroll-setup-guide.component'),
      },
      {
        path: 'signup/verify',
        title: 'register.verify.title',
        loadComponent: () => import('./login/signup-verify.component'),
      },
    ],
  },
  {
    // The SaaS owner console: its own shell, only for platform accounts.
    path: 'platform',
    loadComponent: () => import('./features/platform/console/platform-shell.component'),
    canActivate: [UserRouteAccessService, platformGuard],
    children: [
      {
        path: '',
        title: 'humano.console.nav.overview',
        loadComponent: () => import('./features/platform/console/overview/overview.component'),
      },
      {
        path: 'customers',
        title: 'humano.console.nav.customers',
        loadComponent: () => import('./features/platform/console/customers/customers.component'),
      },
      {
        path: 'customers/:id',
        title: 'humano.console.nav.customers',
        loadComponent: () => import('./features/platform/console/customer-detail/customer-detail.component'),
      },
      { path: 'billing', pathMatch: 'full', redirectTo: 'billing/invoices' },
      {
        path: 'billing/:tab',
        title: 'humano.console.nav.billing',
        loadComponent: () => import('./features/platform/console/billing/console-billing.component'),
      },
      { path: 'demos', pathMatch: 'full', redirectTo: 'demos/requests' },
      {
        path: 'demos/:tab',
        title: 'humano.console.nav.demos',
        loadComponent: () => import('./features/platform/console/demos/demos.component'),
      },
      { path: 'team', title: 'humano.console.nav.team', loadComponent: () => import('./features/platform/console/team/team.component') },
      { path: 'account', pathMatch: 'full', redirectTo: 'account/profile' },
      { path: 'account/:section', title: 'humano.account.title', loadComponent: () => import('./account/account.component') },
      { path: '**', redirectTo: '' },
    ],
  },
  {
    path: '',
    loadComponent: () => import('./layouts/shell-layout.component'),
    canActivate: [UserRouteAccessService, workspaceGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'accessdenied', title: 'error.title', loadComponent: () => import('./layouts/access-denied.component') },
      { path: 'account', pathMatch: 'full', redirectTo: 'account/profile' },
      {
        // Own account: profile, security (password, signed-in devices), preferences. Legacy
        // `settings` / `password` / `sessions` paths open the matching tab.
        path: 'account/:section',
        title: 'humano.account.title',
        loadComponent: () => import('./account/account.component'),
      },
      {
        path: 'dashboard',
        title: 'humano.nav.dashboard',
        loadComponent: () => import('./features/admin/dashboard/dashboard.component'),
        canActivate: [UserRouteAccessService, dashboardGuard],
      },
      { path: 'me', title: 'humano.nav.portal', loadComponent: () => import('./features/employee/portal/portal.component') },
      {
        path: 'my-profile',
        title: 'humano.nav.myProfile',
        loadComponent: () => import('./features/employee/my-profile/my-profile.component'),
      },
      {
        path: 'leave',
        title: 'humano.nav.leave',
        loadComponent: () => import('./features/employee/leave/leave.component'),
      },
      {
        path: 'leave/new',
        title: 'humano.leaveRequest.title',
        loadComponent: () => import('./features/employee/leave-request/leave-request.component'),
      },
      {
        path: 'timesheets',
        title: 'humano.nav.timesheets',
        loadComponent: () => import('./features/employee/timesheets/timesheets.component'),
      },
      {
        path: 'my-documents',
        title: 'humano.nav.myDocuments',
        loadComponent: () => import('./features/employee/my-documents/my-documents.component'),
      },
      {
        path: 'employees',
        title: 'humano.nav.employees',
        loadComponent: () => import('./features/admin/directory/directory.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.READ_EMPLOYEE] },
      },
      {
        path: 'employees/new',
        title: 'humano.employeeForm.createTitle',
        loadComponent: () => import('./features/admin/employee-form/employee-form.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.CREATE_EMPLOYEE] },
      },
      {
        path: 'employees/:id',
        title: 'humano.nav.employees',
        loadComponent: () => import('./features/admin/employee-detail/employee-detail.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.READ_EMPLOYEE] },
      },
      {
        path: 'employees/:id/edit',
        title: 'humano.employeeForm.editTitle',
        loadComponent: () => import('./features/admin/employee-form/employee-form.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.UPDATE_EMPLOYEE] },
      },
      {
        path: 'people-tree',
        title: 'humano.nav.peopleTree',
        loadComponent: () => import('./features/employee/people-tree/people-tree.component').then(m => m.PeopleTreeComponent),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'people-tree/:employeeId',
        title: 'humano.nav.peopleTree',
        loadComponent: () => import('./features/employee/people-tree/people-tree.component').then(m => m.PeopleTreeComponent),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'organization-tree',
        title: 'humano.nav.organizationTree',
        loadComponent: () =>
          import('./features/employee/organization-tree/organization-tree.component').then(m => m.OrganizationTreeComponent),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'organization-tree/:unitId',
        title: 'humano.nav.organizationTree',
        loadComponent: () =>
          import('./features/employee/organization-tree/organization-tree.component').then(m => m.OrganizationTreeComponent),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'onboarding',
        title: 'humano.nav.onboarding',
        loadComponent: () => import('./features/admin/onboarding/onboarding.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_ONBOARDING_PROGRESS, Permission.MANAGE_ONBOARDING] },
      },
      {
        // Before `onboarding/:id`, so "checklist" isn't read as a process id.
        path: 'onboarding/checklist',
        title: 'humano.onboardingChecklist.title',
        loadComponent: () => import('./features/admin/onboarding-checklist/onboarding-checklist.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_ONBOARDING] },
      },
      {
        path: 'onboarding/:id',
        title: 'humano.nav.onboarding',
        loadComponent: () => import('./features/admin/onboarding-detail/onboarding-detail.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_ONBOARDING_PROGRESS, Permission.MANAGE_ONBOARDING] },
      },
      {
        path: 'offboarding',
        title: 'humano.nav.offboarding',
        loadComponent: () => import('./features/admin/offboarding/offboarding.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_OFFBOARDING, Permission.MANAGE_OFFBOARDING] },
      },
      {
        path: 'offboarding/:id',
        title: 'humano.nav.offboarding',
        loadComponent: () => import('./features/admin/offboarding-detail/offboarding-detail.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_OFFBOARDING, Permission.MANAGE_OFFBOARDING] },
      },
      {
        // Everyone: their own review and the ones they write (the API checks each review).
        path: 'reviews',
        title: 'humano.nav.reviews',
        loadComponent: () => import('./features/employee/performance-reviews/performance-reviews.component'),
        canActivate: [UserRouteAccessService],
      },
      {
        // A colleague giving feedback on someone's review (the API checks it's theirs).
        path: 'reviews/feedback/:requestId',
        title: 'humano.nav.reviews',
        loadComponent: () => import('./features/employee/review-feedback/review-feedback.component'),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'reviews/:id',
        title: 'humano.nav.reviews',
        loadComponent: () => import('./features/employee/performance-review/performance-review.component'),
        canActivate: [UserRouteAccessService],
      },
      {
        // Everyone: their own goals and their direct reports' (the API checks the reporting line).
        path: 'goals',
        title: 'humano.nav.goals',
        loadComponent: () => import('./features/employee/goals/goals.component'),
        canActivate: [UserRouteAccessService],
      },
      {
        // The plans one runs, and one's own once shared (the API checks each plan).
        path: 'improvement-plans',
        title: 'humano.improvementPlans.title',
        loadComponent: () => import('./features/employee/improvement-plans/improvement-plans.component'),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'improvement-plans/:id',
        title: 'humano.improvementPlans.title',
        loadComponent: () => import('./features/employee/improvement-plans/improvement-plan.component'),
        canActivate: [UserRouteAccessService],
      },
      {
        path: 'performance-analytics',
        title: 'humano.performanceAnalytics.title',
        loadComponent: () => import('./features/admin/performance-analytics/performance-analytics.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PERFORMANCE_REVIEWS] },
      },
      {
        // Pay decisions after a regular cycle; the cycle is the `cycle` query parameter.
        path: 'compensation-reviews',
        title: 'humano.compensationReview.title',
        loadComponent: () => import('./features/admin/compensation-review/compensation-review.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_COMPENSATION] },
      },
      {
        path: 'review-cycles',
        title: 'humano.nav.reviewCycles',
        loadComponent: () => import('./features/admin/review-cycles/review-cycles.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PERFORMANCE_REVIEWS] },
      },
      {
        // Read-only without MANAGE_PERFORMANCE_FRAMEWORKS.
        path: 'performance-frameworks',
        title: 'humano.performance.framework.title',
        loadComponent: () => import('./features/admin/performance-frameworks/performance-frameworks.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_PERFORMANCE_FRAMEWORKS, Permission.VIEW_PERFORMANCE_REVIEWS] },
      },
      {
        // Before `review-cycles/:id`, so "calendar" isn't read as a cycle id.
        path: 'review-cycles/calendar',
        title: 'humano.performance.calendar.title',
        loadComponent: () => import('./features/admin/review-calendar/review-calendar.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.CONFIGURE_PERFORMANCE_CYCLES] },
      },
      {
        // Before `review-cycles/:id`, like the calendar.
        path: 'review-cycles/retention',
        title: 'humano.performance.retention.title',
        loadComponent: () => import('./features/admin/review-retention/review-retention.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.CONFIGURE_PERFORMANCE_CYCLES] },
      },
      {
        path: 'review-cycles/:id/calibration',
        title: 'humano.performance.calibration.title',
        loadComponent: () => import('./features/admin/review-cycle-calibration/review-cycle-calibration.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PERFORMANCE_REVIEWS] },
      },
      {
        path: 'review-cycles/:id',
        title: 'humano.nav.reviewCycles',
        loadComponent: () => import('./features/admin/review-cycle-detail/review-cycle-detail.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PERFORMANCE_REVIEWS] },
      },
      {
        path: 'org',
        title: 'humano.nav.org',
        loadComponent: () => import('./features/admin/org-units/org-units.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_ORGANIZATIONAL_UNITS] },
      },
      {
        path: 'departments',
        title: 'humano.nav.departments',
        loadComponent: () => import('./features/admin/departments/departments.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_DEPARTMENTS] },
      },
      {
        path: 'positions',
        title: 'humano.nav.positions',
        loadComponent: () => import('./features/admin/positions/positions.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_POSITIONS] },
      },
      {
        path: 'payroll/runs',
        title: 'humano.nav.runs',
        loadComponent: () => import('./features/admin/payroll-runs/payroll-runs.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PAYROLL_RUN] },
      },
      {
        path: 'payroll/runs/:id',
        title: 'humano.nav.runs',
        loadComponent: () => import('./features/admin/payroll-run/payroll-run.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PAYROLL_RUN] },
      },
      {
        path: 'payroll/payslips',
        title: 'humano.nav.payslips',
        loadComponent: () => import('./features/admin/payslips/payslips.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PAYSLIPS] },
      },
      {
        path: 'payroll/payslips/:id',
        title: 'humano.nav.payslips',
        loadComponent: () => import('./features/admin/payslip/payslip.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PAYSLIPS] },
      },
      {
        // Self-service payslip (own payslips only, enforced by /api/me/payslips/{id}).
        path: 'my-payslips/:id',
        title: 'humano.nav.payslips',
        loadComponent: () => import('./features/admin/payslip/payslip.component'),
        canActivate: [UserRouteAccessService],
        data: { self: true },
      },
      {
        path: 'approvals',
        title: 'humano.nav.approvals',
        // Approvals follow reporting lines (whoever a request is routed to), not roles — every
        // signed-in employee gets the inbox; the API only returns and accepts their own items.
        loadComponent: () => import('./features/manager/approvals/approvals.component'),
      },
      {
        path: 'payroll/pay-rules',
        title: 'humano.nav.payRules',
        loadComponent: () => import('./features/admin/pay-rules/pay-rule-editor.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_PAY_COMPONENTS] },
      },
      {
        path: 'payroll/leave-pay',
        title: 'humano.nav.leavePay',
        loadComponent: () => import('./features/admin/leave-pay/leave-pay-rules.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_PAY_COMPONENTS] },
      },
      {
        path: 'payroll/templates',
        title: 'humano.payrollTemplates.title',
        loadComponent: () => import('./features/admin/payroll-templates/payroll-templates.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_TAX_BRACKETS] },
      },
      {
        path: 'payroll/templates/:id',
        title: 'humano.payrollTemplates.title',
        loadComponent: () => import('./features/admin/payroll-templates/payroll-template.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.MANAGE_TAX_BRACKETS] },
      },
      {
        path: 'settings',
        title: 'humano.nav.settings',
        loadComponent: () => import('./features/admin/settings/settings.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.CONFIGURE_TENANT_SETTINGS] },
      },
      {
        path: 'settings/:section',
        title: 'humano.nav.settings',
        loadComponent: () => import('./features/admin/settings/settings.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.CONFIGURE_TENANT_SETTINGS] },
      },
      { path: '**', title: 'error.notFound.title', loadComponent: () => import('./layouts/not-found.component') },
    ],
  },
];

export default routes;
