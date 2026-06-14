import { AuditFields } from 'app/core/api';

/** `UserResponse` — admin-managed user (`/api/admin/users`). */
export interface ManagedUser extends AuditFields {
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

export interface CreateUserRequest {
  login: string;
  firstName?: string;
  lastName?: string;
  email: string;
  imageUrl?: string;
  langKey?: string;
  authorities?: string[];
}

export interface UpdateUserRequest {
  id: string;
  login: string;
  firstName?: string;
  lastName?: string;
  email: string;
  imageUrl?: string;
  activated: boolean;
  langKey?: string;
  authorities?: string[];
}
