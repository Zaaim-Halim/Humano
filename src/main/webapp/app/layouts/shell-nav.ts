import { Authority } from 'app/config/authority.constants';

/**
 * Single source of truth for the authenticated navigation. Both the sidebar
 * (via `AppShell`) and the ⌘K command palette derive from this list, so a route
 * never drifts between the two. `roles` gates visibility against the account's
 * authorities; omit it to show the item to any authenticated user.
 *
 * Labels are i18n keys (resolved by the consumer, since the design-system
 * components take already-translated strings) — see `i18n/en/navigation.json`.
 * Mirrors the reference `ui-kits/hr-admin/nav.js` IA, with self-service items
 * ungated and operational/admin sections behind `ROLE_ADMIN`.
 */
export interface ShellNavItem {
  id: string;
  /** i18n key for the visible label. */
  labelKey: string;
  icon: string;
  /** Absolute router path the item navigates to. */
  link: string;
  badge?: string | number;
  /** Extra (already-localised or language-neutral) terms folded into the command-palette match. */
  keywords?: string;
  /** Authorities allowed to see this item; omit = any authenticated user. */
  roles?: string[];
}

export interface ShellNavGroup {
  /** i18n key for the group heading. */
  headingKey: string;
  /** Gate the whole group; individual items can narrow further. */
  roles?: string[];
  items: ShellNavItem[];
}

export const SHELL_NAV: ShellNavGroup[] = [
  {
    headingKey: 'humano.nav.groups.overview',
    items: [{ id: 'dashboard', labelKey: 'humano.nav.dashboard', icon: 'layout-grid', link: '/dashboard', keywords: 'home overview kpis' }],
  },
  {
    headingKey: 'humano.nav.groups.people',
    roles: [Authority.ADMIN],
    items: [
      { id: 'employees', labelKey: 'humano.nav.employees', icon: 'users', link: '/employees', keywords: 'people directory staff' },
      { id: 'org', labelKey: 'humano.nav.org', icon: 'network', link: '/org', keywords: 'organization departments teams' },
      { id: 'positions', labelKey: 'humano.nav.positions', icon: 'briefcase', link: '/positions', keywords: 'jobs roles openings' },
    ],
  },
  {
    headingKey: 'humano.nav.groups.timeLeave',
    items: [
      { id: 'leave', labelKey: 'humano.nav.leave', icon: 'palmtree', link: '/leave', keywords: 'holiday vacation pto absence' },
      { id: 'timesheets', labelKey: 'humano.nav.timesheets', icon: 'clock', link: '/timesheets', keywords: 'hours attendance' },
    ],
  },
  {
    headingKey: 'humano.nav.groups.payroll',
    roles: [Authority.ADMIN],
    items: [
      { id: 'runs', labelKey: 'humano.nav.runs', icon: 'wallet', link: '/payroll/runs', keywords: 'payroll salary process' },
      {
        id: 'payslips',
        labelKey: 'humano.nav.payslips',
        icon: 'receipt',
        link: '/payroll/payslips',
        keywords: 'salary slip pay statement',
      },
      { id: 'approvals', labelKey: 'humano.nav.approvals', icon: 'check-check', link: '/approvals', keywords: 'pending review sign off' },
    ],
  },
  {
    headingKey: 'humano.nav.groups.settings',
    roles: [Authority.ADMIN],
    items: [
      { id: 'settings', labelKey: 'humano.nav.settings', icon: 'settings', link: '/settings', keywords: 'configuration preferences admin' },
      { id: 'users', labelKey: 'humano.nav.users', icon: 'user', link: '/admin/users', keywords: 'users accounts roles admin' },
    ],
  },
];
