import { Injectable, signal } from '@angular/core';

/**
 * Resolves the signed-in user to their own employee record — the single seam
 * the self-service (Employee persona) screens depend on.
 *
 * TODO: backend endpoint missing — there is no self-service way to obtain the
 * current user's `employeeId`. `GET /api/account` (`MeResponse`) returns the
 * user id, name, email and authorities but NOT an employeeId, and the only
 * employee lookups (`GET /api/hr/employees`, `/{id}`, `POST /search`) are gated
 * `@RequireHrStaff` (ADMIN/HR only), so a plain `ROLE_EMPLOYEE` cannot discover
 * it. Until a `GET /api/me/employee` (or an `employeeId` claim on the account)
 * lands, this resolves to `null` and the employee data screens degrade to their
 * empty/TODO states rather than fabricating a record or rendering someone
 * else's PII off an unverifiable match.
 *
 * When the endpoint exists, wire it in {@link resolve}; every self-service
 * screen already reads {@link currentEmployeeId} reactively, so nothing else
 * needs to change.
 */
@Injectable({ providedIn: 'root' })
export class CurrentEmployeeService {
  /** The signed-in user's own employee id, or `null` while unresolved/unavailable. */
  readonly currentEmployeeId = signal<string | null>(null);

  /** `true` once a resolution attempt has completed (success or known-unavailable). */
  readonly resolved = signal(false);

  /**
   * Attempt to resolve the current user's employee id.
   *
   * No-op today: no backend endpoint exposes it (see class doc). Marks
   * {@link resolved} so callers can distinguish "still loading" from
   * "resolved, but no self-service mapping exists".
   */
  resolve(): void {
    // TODO: backend — replace with `GET /api/me/employee` when available:
    //   this.http.get<{ id: string }>('api/me/employee')
    //     .subscribe({ next: e => this.currentEmployeeId.set(e.id), ... });
    this.resolved.set(true);
  }
}
