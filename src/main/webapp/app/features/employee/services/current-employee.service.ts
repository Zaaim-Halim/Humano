import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';

import { AccountService } from 'app/core/auth/account.service';
import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { EmployeeProfile } from '../models/employee.model';

/**
 * Resolves the signed-in user to their own employee record — the single seam
 * the self-service (Employee persona) screens depend on.
 *
 * <p>Backed by `GET /api/me/employee`, which derives the record from the security
 * context (never from a caller-supplied id) and is open to any authenticated user,
 * unlike the HR-gated `/api/hr/employees/{id}`. An account with no employee row —
 * an admin created outside employee provisioning — gets a 404, which lands here as
 * `null` so the screens fall back to their empty states instead of erroring.
 */
@Injectable({ providedIn: 'root' })
export class CurrentEmployeeService {
  /** The signed-in user's own employee record, or `null` while unresolved/unavailable. */
  readonly currentEmployee = signal<EmployeeProfile | null>(null);

  /** The signed-in user's own employee id, or `null` while unresolved/unavailable. */
  readonly currentEmployeeId = computed(() => this.currentEmployee()?.id ?? null);

  /** `true` once a resolution attempt has completed (success or known-unavailable). */
  readonly resolved = signal(false);

  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);
  private readonly account = inject(AccountService).trackCurrentAccount();
  private inFlight = false;
  /** Login the cached id belongs to, so a second user in the same tab can't inherit it. */
  private resolvedFor: string | null = null;

  constructor() {
    effect(() => {
      const login = this.account()?.login ?? null;
      untracked(() => {
        if (login === this.resolvedFor) return;
        this.resolvedFor = login;
        this.reset();
        if (login) this.resolve();
      });
    });
  }

  /**
   * Attempt to resolve the current user's employee record. Cheap to call from every
   * self-service screen — it only hits the network until one attempt completes, and the
   * whole profile is kept so a "my profile" view needs no second request.
   *
   * @param force re-fetch even though an attempt already completed; pass this after a
   *              sign-in so a new identity never inherits the previous user's record.
   */
  resolve(force = false): void {
    if (this.inFlight || (this.resolved() && !force)) return;
    this.inFlight = true;
    this.http.get<EmployeeProfile>(this.config.getEndpointFor('api/me/employee')).subscribe({
      next: employee => {
        this.currentEmployee.set(employee);
        this.resolved.set(true);
        this.inFlight = false;
      },
      error: () => {
        // 404 = authenticated but not an employee; any other failure is no usable mapping either.
        this.currentEmployee.set(null);
        this.resolved.set(true);
        this.inFlight = false;
      },
    });
  }

  /** Drop the cached record so the next user resolves afresh. Call on sign-out. */
  reset(): void {
    this.currentEmployee.set(null);
    this.resolved.set(false);
    this.inFlight = false;
  }
}
