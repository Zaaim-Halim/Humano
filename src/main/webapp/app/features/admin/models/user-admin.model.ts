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
