import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Invoice } from '../models/invoice.model';
import { Subscription } from '../models/subscription.model';

/**
 * Platform/superadmin billing — `/api/platform/billing` (`@RequireAdmin`). These
 * are cross-tenant reads with no tenant-ownership check, so they power the
 * superadmin per-tenant billing drill-in from Tenant Management.
 *
 * Only the by-tenant reads are exposed here (the surface that consumes them).
 * Per-tenant *payment* detail is intentionally absent: the tenant-scoped
 * `/api/billing/payments/by-invoice/{id}` enforces `verifyTenantOwnership`, so a
 * superadmin viewing another tenant's invoice is denied; the only cross-tenant
 * payments endpoint (`/payments`) is unfiltered. Invoices already carry a
 * `paymentCount`, which the drill-in surfaces instead.
 */
@Injectable({ providedIn: 'root' })
export class PlatformBillingService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/platform/billing');

  /** `GET /api/platform/billing/subscriptions/by-tenant/{tenantId}` — the tenant's current subscription. */
  subscriptionByTenant(tenantId: string): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.resourceUrl}/subscriptions/by-tenant/${encodeURIComponent(tenantId)}`);
  }

  /** `GET /api/platform/billing/invoices/by-tenant/{tenantId}` — the tenant's invoices (unpaged). */
  invoicesByTenant(tenantId: string): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.resourceUrl}/invoices/by-tenant/${encodeURIComponent(tenantId)}`);
  }
}
