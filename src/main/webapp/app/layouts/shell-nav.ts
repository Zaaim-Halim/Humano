import { Permission } from 'app/config/permission.constants';

/**
 * Single source of truth for the authenticated navigation. Both the sidebar
 * (via `AppShell`) and the ⌘K command palette derive from this list, so a route
 * never drifts between the two. `permissions` gates visibility against the
 * account's effective permissions (any-of); omit it to show the item to any
 * authenticated user.
 *
 * Labels are i18n keys (resolved by the consumer, since the design-system
 * components take already-translated strings) — see `i18n/en/navigation.json`.
 * Permission gating mirrors the backend `@RequirePermission` checks, so a hidden
 * item is also a forbidden endpoint. The platform group is gated on platform
 * permissions, which only exist in the platform tenant — so it no longer shows
 * to ordinary tenant admins.
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
  /** Permissions allowed to see this item (any-of); omit = any authenticated user. */
  permissions?: string[];
}

export interface ShellNavGroup {
  /** i18n key for the group heading. */
  headingKey: string;
  /** Gate the whole group (any-of); individual items can narrow further. */
  permissions?: string[];
  items: ShellNavItem[];
}

export const SHELL_NAV: ShellNavGroup[] = [
  {
    headingKey: 'humano.nav.groups.overview',
    items: [
      { id: 'dashboard', labelKey: 'humano.nav.dashboard', icon: 'layout-grid', link: '/dashboard', keywords: 'home overview kpis' },
      { id: 'portal', labelKey: 'humano.nav.portal', icon: 'user', link: '/me', keywords: 'me self service portal home my space' },
    ],
  },
  {
    headingKey: 'humano.nav.groups.people',
    permissions: [Permission.READ_EMPLOYEE, Permission.VIEW_ORGANIZATIONAL_UNITS, Permission.VIEW_POSITIONS, Permission.VIEW_DEPARTMENTS],
    items: [
      {
        id: 'employees',
        labelKey: 'humano.nav.employees',
        icon: 'users',
        link: '/employees',
        keywords: 'people directory staff',
        permissions: [Permission.READ_EMPLOYEE],
      },
      {
        id: 'people-tree',
        labelKey: 'humano.nav.peopleTree',
        icon: 'git-fork',
        link: '/people-tree',
        keywords: 'reporting hierarchy managers org chart subordinates',
        permissions: [Permission.READ_EMPLOYEE],
      },
      {
        id: 'org',
        labelKey: 'humano.nav.org',
        icon: 'network',
        link: '/org',
        keywords: 'organization units teams sectors',
        permissions: [Permission.VIEW_ORGANIZATIONAL_UNITS],
      },
      {
        id: 'departments',
        labelKey: 'humano.nav.departments',
        icon: 'layers',
        link: '/departments',
        keywords: 'departments division team head',
        permissions: [Permission.VIEW_DEPARTMENTS],
      },
      {
        id: 'organization-tree',
        labelKey: 'humano.nav.organizationTree',
        icon: 'list-tree',
        link: '/organization-tree',
        keywords: 'org chart units departments sectors hierarchy structure',
        permissions: [Permission.VIEW_ORGANIZATIONAL_UNITS],
      },
      {
        id: 'positions',
        labelKey: 'humano.nav.positions',
        icon: 'briefcase',
        link: '/positions',
        keywords: 'jobs roles openings',
        permissions: [Permission.VIEW_POSITIONS],
      },
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
    permissions: [Permission.VIEW_PAYROLL_RUN, Permission.VIEW_PAYSLIPS, Permission.APPROVE_LEAVE, Permission.MANAGE_PAY_COMPONENTS],
    items: [
      {
        id: 'runs',
        labelKey: 'humano.nav.runs',
        icon: 'wallet',
        link: '/payroll/runs',
        keywords: 'payroll salary process',
        permissions: [Permission.VIEW_PAYROLL_RUN],
      },
      {
        id: 'pay-rules',
        labelKey: 'humano.nav.payRules',
        icon: 'function-square',
        link: '/payroll/pay-rules',
        keywords: 'formula rule pay component calculation tax',
        permissions: [Permission.MANAGE_PAY_COMPONENTS],
      },
      {
        id: 'payslips',
        labelKey: 'humano.nav.payslips',
        icon: 'receipt',
        link: '/payroll/payslips',
        keywords: 'salary slip pay statement',
        permissions: [Permission.VIEW_PAYSLIPS],
      },
      {
        id: 'approvals',
        labelKey: 'humano.nav.approvals',
        icon: 'check-check',
        link: '/approvals',
        keywords: 'pending review sign off',
        permissions: [Permission.APPROVE_LEAVE, Permission.APPROVE_EXPENSE_CLAIMS, Permission.APPROVE_OVERTIME],
      },
    ],
  },
  {
    headingKey: 'humano.nav.groups.settings',
    permissions: [Permission.SYSTEM_CONFIGURATION, Permission.READ_USER],
    items: [
      {
        id: 'settings',
        labelKey: 'humano.nav.settings',
        icon: 'settings',
        link: '/settings',
        keywords: 'configuration preferences admin',
        permissions: [Permission.SYSTEM_CONFIGURATION],
      },
      {
        id: 'users',
        labelKey: 'humano.nav.users',
        icon: 'user',
        link: '/admin/users',
        keywords: 'users accounts roles admin',
        permissions: [Permission.READ_USER],
      },
    ],
  },
  {
    headingKey: 'humano.nav.groups.platform',
    permissions: [Permission.VIEW_PLATFORM_TENANTS, Permission.VIEW_PLATFORM_BILLING],
    items: [
      {
        id: 'tenants',
        labelKey: 'humano.nav.tenants',
        icon: 'building-2',
        link: '/platform/tenants',
        keywords: 'tenants superadmin platform',
        permissions: [Permission.VIEW_PLATFORM_TENANTS],
      },
      {
        id: 'billing',
        labelKey: 'humano.nav.billing',
        icon: 'credit-card',
        link: '/platform/billing',
        keywords: 'billing invoices subscription plan payment',
        permissions: [Permission.VIEW_PLATFORM_BILLING],
      },
    ],
  },
];
