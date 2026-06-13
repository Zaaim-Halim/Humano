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
  {
    path: '',
    loadComponent: () => import('./layouts/shell-layout.component'),
    canActivate: [UserRouteAccessService],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      page('dashboard', 'humano.nav.dashboard'),
      page('leave', 'humano.nav.leave'),
      page('timesheets', 'humano.nav.timesheets'),
      page('employees', 'humano.nav.employees', [Authority.ADMIN]),
      page('org', 'humano.nav.org', [Authority.ADMIN]),
      page('positions', 'humano.nav.positions', [Authority.ADMIN]),
      page('payroll/runs', 'humano.nav.runs', [Authority.ADMIN]),
      page('payroll/payslips', 'humano.nav.payslips', [Authority.ADMIN]),
      page('approvals', 'humano.nav.approvals', [Authority.ADMIN]),
      page('settings', 'humano.nav.settings', [Authority.ADMIN]),
    ],
  },
];

export default routes;
