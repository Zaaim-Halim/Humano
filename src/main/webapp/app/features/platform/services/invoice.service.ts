import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { Invoice } from '../models/invoice.model';

/**
 * The signed-in customer's own invoices (read-only) — `/api/billing/invoices`, an unpaged list.
 * Invoices are issued and settled by the platform (`PlatformBillingService`).
 */
@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/billing/invoices');

  /** `GET /api/billing/invoices`. */
  list(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.resourceUrl);
  }

  /** `GET /api/billing/invoices/{id}`. */
  find(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `GET /api/billing/invoices/{id}/pdf` — the invoice document. */
  pdf(id: string): Observable<Blob> {
    return this.http.get(`${this.resourceUrl}/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' });
  }
}
