/** `UserResponse` from `/api/admin/users`. */
export interface AdminUser {
  id: string;
  login: string;
  firstName: string | null;
  lastName: string | null;
  email: string;
  imageUrl: string | null;
  activated: boolean;
  langKey: string | null;
  createdDate: string | null;
  lastModifiedDate: string | null;
  authorities: string[];
}

/** `PUT /api/admin/users` body. */
export interface UpdateAdminUserRequest {
  id: string;
  login: string;
  firstName: string | null;
  lastName: string | null;
  email: string;
  imageUrl: string | null;
  activated: boolean;
  langKey: string | null;
  authorities: string[];
}

/** `GET /api/authorities/overview` — a role and the permissions it grants. */
export interface RolePermissions {
  name: string;
  permissions: string[];
  /** How many users hold the role. */
  userCount: number;
}

/** "ROLE_HR_MANAGER" → "HR manager"; "VIEW_PAYROLL_RUN" → "View payroll run". */
export function humanizeCode(code: string): string {
  const ACRONYMS = new Set(['HR', 'IT', 'API', 'PTO', 'ID', 'SSO']);
  const words = code
    .replace(/^ROLE_/, '')
    .split('_')
    .filter(Boolean)
    .map((w, i) => (ACRONYMS.has(w) ? w : i === 0 ? w.charAt(0) + w.slice(1).toLowerCase() : w.toLowerCase()));
  return words.join(' ');
}

/** Roles grouped by area for the role picker and the roles overview. */
export const ROLE_GROUPS: { id: string; icon: string; roles: string[] }[] = [
  {
    id: 'core',
    icon: 'shield-check',
    roles: [
      'ROLE_ADMIN',
      'ROLE_TENANT_ADMIN',
      'ROLE_ORGANIZATION_ADMIN',
      'ROLE_EXECUTIVE',
      'ROLE_DEPARTMENT_HEAD',
      'ROLE_MANAGER',
      'ROLE_EMPLOYEE',
      'ROLE_USER',
    ],
  },
  {
    id: 'people',
    icon: 'users',
    roles: [
      'ROLE_HR_MANAGER',
      'ROLE_HR_SPECIALIST',
      'ROLE_RECRUITER',
      'ROLE_ONBOARDING_SPECIALIST',
      'ROLE_PERFORMANCE_ADMIN',
      'ROLE_PERFORMANCE_REVIEWER',
      'ROLE_TRAINING_ADMIN',
      'ROLE_TRAINING_COORDINATOR',
    ],
  },
  { id: 'time', icon: 'timer', roles: ['ROLE_LEAVE_ADMIN', 'ROLE_LEAVE_APPROVER', 'ROLE_ATTENDANCE_ADMIN', 'ROLE_TIMESHEET_APPROVER'] },
  {
    id: 'money',
    icon: 'wallet',
    roles: [
      'ROLE_PAYROLL_ADMIN',
      'ROLE_PAYROLL_SPECIALIST',
      'ROLE_PAYROLL_VIEWER',
      'ROLE_BENEFITS_ADMIN',
      'ROLE_BENEFITS_SPECIALIST',
      'ROLE_EXPENSE_ADMIN',
      'ROLE_EXPENSE_APPROVER',
      'ROLE_FINANCE_MANAGER',
      'ROLE_FINANCE_SPECIALIST',
      'ROLE_BILLING_ADMIN',
      'ROLE_BILLING_SPECIALIST',
    ],
  },
  {
    id: 'oversight',
    icon: 'eye',
    roles: [
      'ROLE_AUDITOR',
      'ROLE_COMPLIANCE_OFFICER',
      'ROLE_REPORT_ADMIN',
      'ROLE_REPORT_VIEWER',
      'ROLE_IT_SUPPORT',
      'ROLE_INTEGRATION_ADMIN',
    ],
  },
];
