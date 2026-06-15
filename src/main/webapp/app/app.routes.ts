import { Routes } from '@angular/router';

import { Authority } from 'app/config/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';

const placeholder = () => import('./layouts/placeholder-page.component');

/**
 * Build a placeholder route. `titleKey` drives both the browser title (via
 * `AppPageTitleStrategy`) and the in-page header. `authorities`, when set, gate
 * the route with the existing auth guard.
 */
const page = (path: string, titleKey: string, authorities?: string[]) => ({
  path,
  title: titleKey,
  loadComponent: placeholder,
  data: { titleKey, ...(authorities ? { authorities } : {}) },
  ...(authorities ? { canActivate: [UserRouteAccessService] } : {}),
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
  // Public auth screens (outside the shell chrome).
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
      page('timesheets', 'humano.nav.timesheets'),
      {
        path: 'employees',
        title: 'humano.nav.employees',
        loadComponent: () => import('./features/admin/directory/directory.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
      {
        path: 'employees/:id',
        title: 'humano.nav.employees',
        loadComponent: () => import('./features/admin/employee-detail/employee-detail.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
      page('org', 'humano.nav.org', [Authority.ADMIN]),
      page('positions', 'humano.nav.positions', [Authority.ADMIN]),
      page('payroll/runs', 'humano.nav.runs', [Authority.ADMIN]),
      {
        path: 'payroll/runs/:id',
        title: 'humano.nav.runs',
        loadComponent: () => import('./features/admin/payroll-run/payroll-run.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
      page('payroll/payslips', 'humano.nav.payslips', [Authority.ADMIN]),
      {
        path: 'payroll/payslips/:id',
        title: 'humano.nav.payslips',
        loadComponent: () => import('./features/admin/payslip/payslip.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
      page('approvals', 'humano.nav.approvals', [Authority.ADMIN]),
      page('settings', 'humano.nav.settings', [Authority.ADMIN]),
      {
        path: 'admin/users',
        title: 'humano.nav.users',
        loadComponent: () => import('./features/admin/user-management/user-management.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
      {
        path: 'platform/tenants',
        title: 'humano.nav.tenants',
        loadComponent: () => import('./features/platform/tenant-management/tenant-management.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
      {
        path: 'platform/billing',
        title: 'humano.nav.billing',
        loadComponent: () => import('./features/platform/billing/billing.component'),
        canActivate: [UserRouteAccessService],
        data: { authorities: [Authority.ADMIN] },
      },
    ],
  },
];

export default routes;
