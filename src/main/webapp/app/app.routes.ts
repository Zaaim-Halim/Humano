import { Routes } from '@angular/router';

import { Permission } from 'app/config/permission.constants';
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
        path: 'register',
        title: 'register.title',
        loadComponent: () => import('./login/register.component'),
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
    ],
  },
  {
    path: '',
    loadComponent: () => import('./layouts/shell-layout.component'),
    canActivate: [UserRouteAccessService],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'accessdenied', title: 'error.title', loadComponent: () => import('./layouts/access-denied.component') },
      {
        path: 'account/settings',
        title: 'global.menu.account.settings',
        loadComponent: () => import('./account/settings/settings.component'),
      },
      {
        path: 'account/password',
        title: 'global.menu.account.password',
        loadComponent: () => import('./account/password/password.component'),
      },
      {
        path: 'account/sessions',
        title: 'global.menu.account.sessions',
        loadComponent: () => import('./account/sessions/sessions.component'),
      },
      { path: 'dashboard', title: 'humano.nav.dashboard', loadComponent: () => import('./features/admin/dashboard/dashboard.component') },
      { path: 'me', title: 'humano.nav.portal', loadComponent: () => import('./features/employee/portal/portal.component') },
      {
        path: 'leave',
        title: 'humano.nav.leave',
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
        data: { permissions: [Permission.READ_EMPLOYEE] },
      },
      {
        path: 'people-tree/:employeeId',
        title: 'humano.nav.peopleTree',
        loadComponent: () => import('./features/employee/people-tree/people-tree.component').then(m => m.PeopleTreeComponent),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.READ_EMPLOYEE] },
      },
      {
        path: 'organization-tree',
        title: 'humano.nav.organizationTree',
        loadComponent: () =>
          import('./features/employee/organization-tree/organization-tree.component').then(m => m.OrganizationTreeComponent),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_ORGANIZATIONAL_UNITS] },
      },
      {
        path: 'organization-tree/:unitId',
        title: 'humano.nav.organizationTree',
        loadComponent: () =>
          import('./features/employee/organization-tree/organization-tree.component').then(m => m.OrganizationTreeComponent),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_ORGANIZATIONAL_UNITS] },
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
        path: 'approvals',
        title: 'humano.nav.approvals',
        loadComponent: () => import('./features/manager/approvals/approvals.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.APPROVE_LEAVE, Permission.APPROVE_EXPENSE_CLAIMS, Permission.APPROVE_OVERTIME] },
      },
      page('settings', 'humano.nav.settings', [Permission.SYSTEM_CONFIGURATION]),
      {
        path: 'admin/users',
        title: 'humano.nav.users',
        loadComponent: () => import('./features/admin/user-management/user-management.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.READ_USER] },
      },
      {
        path: 'platform/tenants',
        title: 'humano.nav.tenants',
        loadComponent: () => import('./features/platform/tenant-management/tenant-management.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PLATFORM_TENANTS] },
      },
      {
        path: 'platform/billing',
        title: 'humano.nav.billing',
        loadComponent: () => import('./features/platform/billing/billing.component'),
        canActivate: [UserRouteAccessService],
        data: { permissions: [Permission.VIEW_PLATFORM_BILLING] },
      },
    ],
  },
];

export default routes;
