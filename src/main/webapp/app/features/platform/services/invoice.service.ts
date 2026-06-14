import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

import { CreateInvoiceRequest, Invoice } from '../models/invoice.model';

/**
 * Invoices — `/api/billing/invoices`. List is an unpaged `List` (not `Page`);
 * `markPaid`/`delete` are financial — confirm in the UI.
 */
@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/billing/invoices');

  /** `GET /api/billing/invoices` — unpaged list. */
  list(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.resourceUrl);
  }

  /** `GET /api/billing/invoices/{id}`. */
  find(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  /** `POST /api/billing/invoices`. */
  create(body: CreateInvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(this.resourceUrl, body);
  }

  /** `POST /api/billing/invoices/{id}/pay` (financial — confirm first). */
  markPaid(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.resourceUrl}/${encodeURIComponent(id)}/pay`, {});
  }

  /** `POST /api/billing/invoices/{id}/mark-overdue`. */
  markOverdue(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.resourceUrl}/${encodeURIComponent(id)}/mark-overdue`, {});
  }

  /** `DELETE /api/billing/invoices/{id}` (void — confirm first). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${encodeURIComponent(id)}`);
  }

  // TODO: backend endpoint missing — no per-invoice PDF endpoint exists
  // (`GET /api/me/billing/invoices/{id}/pdf` in the spec is not implemented).
}
