import { Injectable, Signal, signal } from '@angular/core';

const STORAGE_KEY = 'humano-tenant-id';

/**
 * Holds the current tenant (organization) identifier sent as the `X-Tenant-ID` header on API calls.
 *
 * The backend resolves the tenant per request from that header (or, in production, the subdomain), so
 * the value chosen at login must travel with every subsequent request — hence it is persisted to
 * `sessionStorage` and attached by {@link TenantInterceptor}. Cleared on logout.
 *
 * This is the dev/interim path; production will switch to subdomain-based resolution, after which the
 * login field can be dropped.
 */
@Injectable({ providedIn: 'root' })
export class TenantContextService {
  /** Current tenant id, or `null` for the platform/master context. */
  readonly tenantId: Signal<string | null>;

  private readonly tenant = signal<string | null>(this.readStored());

  constructor() {
    this.tenantId = this.tenant.asReadonly();
  }

  setTenant(id: string): void {
    const normalized = id.trim().toLowerCase();
    if (!normalized) {
      return;
    }
    sessionStorage.setItem(STORAGE_KEY, normalized);
    this.tenant.set(normalized);
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.tenant.set(null);
  }

  private readStored(): string | null {
    try {
      return sessionStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
