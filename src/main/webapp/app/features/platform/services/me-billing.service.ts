import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { PublicPricing } from 'app/features/marketing/marketing.service';

import { BillingCycle } from '../models/enums/billing-cycle.enum';
import { Entitlements, StorageAddon } from '../models/entitlements.model';
import { Subscription } from '../models/subscription.model';

/**
 * Self-service billing — `/api/billing/me`: the company's own subscription, and (with
 * `MANAGE_SUBSCRIPTIONS`) changing plan, cancelling at period end and taking that back. Demo
 * workspaces are refused by the server.
 */
@Injectable({ providedIn: 'root' })
export class MeBillingService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);
  private readonly resourceUrl = this.config.getEndpointFor('api/billing/me');

  /**
   * The last subscription fetched by {@link currentSubscription} — `null` when there is none (demo
   * workspaces), `undefined` until first fetched. Drives the shell's trial banner, and so follows
   * every reload (e.g. billing settings after a plan change).
   */
  readonly current = signal<Subscription | null | undefined>(undefined);

  /**
   * What the plan allows, with current usage — `null` until loaded (or when it cannot be). Drives the
   * locked nav items, the read-only notice on modules outside the plan and the usage meters.
   */
  readonly entitlements = signal<Entitlements | null>(null);

  /** `GET /api/billing/me/entitlements`, kept in {@link entitlements}. */
  loadEntitlements(): void {
    this.http.get<Entitlements>(`${this.resourceUrl}/entitlements`).subscribe({
      next: e => this.entitlements.set(e),
      error: () => this.entitlements.set(null),
    });
  }

  /** Extra storage on top of the plan: offer, price and packs held. */
  storageAddon(): Observable<StorageAddon> {
    return this.http.get<StorageAddon>(`${this.resourceUrl}/addons/storage`);
  }

  /** Sets the number of extra storage packs (0 removes them); billed monthly on top of the subscription. */
  setStorageAddon(packs: number): Observable<StorageAddon> {
    return this.http.put<StorageAddon>(`${this.resourceUrl}/addons/storage`, { packs });
  }

  /** `GET /api/billing/me/current-subscription` (404 when there is none). */
  currentSubscription(): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.resourceUrl}/current-subscription`).pipe(
      tap({
        next: s => this.current.set(s),
        error: () => this.current.set(null),
      }),
    );
  }

  /** The plans on sale, with what each includes (the public pricing data). */
  plans(): Observable<PublicPricing> {
    return this.http.get<PublicPricing>(this.config.getEndpointFor('api/public/pricing'));
  }

  changePlan(planId: string, billingCycle: BillingCycle): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.resourceUrl}/change-plan`, { planId, billingCycle });
  }

  cancel(): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.resourceUrl}/cancel`, {});
  }

  resume(): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.resourceUrl}/resume`, {});
  }
}
