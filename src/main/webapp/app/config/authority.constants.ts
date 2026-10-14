export enum Authority {
  ADMIN = 'ROLE_ADMIN',
  USER = 'ROLE_USER',
  PLATFORM_OWNER = 'ROLE_PLATFORM_OWNER',
  PLATFORM_ADMIN = 'ROLE_PLATFORM_ADMIN',
}

/** SaaS-owner roles: they live only in the platform tenant and have no HR workspace. */
export const PLATFORM_ROLES: string[] = [Authority.PLATFORM_OWNER, Authority.PLATFORM_ADMIN];
