import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { Page } from 'app/core/api';
import { createRequestOption } from 'app/core/request/request-util';

import { Invoice } from '../models/invoice.model';
import { Coupon, CreateCouponRequest, FeatureRequest, PlanComparison, PlanFeatureCell, PlanFeatureDef } from '../models/platform.model';
import { CreateSubscriptionPlanRequest, SubscriptionPlan, UpdateSubscriptionPlanRequest } from '../models/subscription-plan.model';
import { AddonRow, CustomerStorage, StorageAddon } from '../models/entitlements.model';
import { Subscription, UpdateSubscriptionRequest } from '../models/subscription.model';

/**
 * Platform (SaaS owner) billing — `/api/platform/billing`. Cross-customer reads (subscriptions,
 * invoices, coupons) and every billing write: plans, coupons, subscription changes, invoice
 * status. These records are shared by all customers, so only the platform realm can change them;
 * customers only read their own billing on `/api/billing/**`.
 */
@Injectable({ providedIn: 'root' })
export class PlatformBillingService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApplicationConfigService);
  private readonly url = this.config.getEndpointFor('api/platform/billing');

  // ---- Reads
  subscriptions(size = 1000): Observable<Page<Subscription>> {
    return this.http.get<Page<Subscription>>(`${this.url}/subscriptions`, { params: createRequestOption({ size }) });
  }

  /** Every customer's add-ons (extra storage), newest first. */
  addons(): Observable<AddonRow[]> {
    return this.http.get<AddonRow[]>(`${this.url}/addons`);
  }

  /** A customer's storage: packs held, the limit with them, and what is used. */
  customerStorage(tenantId: string): Observable<CustomerStorage> {
    return this.http.get<CustomerStorage>(`${this.url}/addons/storage/by-tenant/${tenantId}`);
  }

  /** Sets a customer's extra storage packs (0 removes them), optionally at a negotiated monthly price per pack. */
  setCustomerStorage(tenantId: string, packs: number, unitPrice: number | null): Observable<StorageAddon> {
    return this.http.put<StorageAddon>(`${this.url}/addons/storage/by-tenant/${tenantId}`, { packs, unitPrice });
  }

  subscriptionByTenant(tenantId: string): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.url}/subscriptions/by-tenant/${encodeURIComponent(tenantId)}`);
  }

  invoices(size = 1000): Observable<Page<Invoice>> {
    return this.http.get<Page<Invoice>>(`${this.url}/invoices`, { params: createRequestOption({ size, sort: 'issueDate,desc' }) });
  }

  invoicesByTenant(tenantId: string): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.url}/invoices/by-tenant/${encodeURIComponent(tenantId)}`);
  }

  coupons(size = 200): Observable<Page<Coupon>> {
    return this.http.get<Page<Coupon>>(`${this.url}/coupons`, { params: createRequestOption({ size }) });
  }

  // ---- Plans
  createPlan(body: CreateSubscriptionPlanRequest): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(`${this.url}/plans`, body);
  }

  updatePlan(id: string, body: UpdateSubscriptionPlanRequest): Observable<SubscriptionPlan> {
    return this.http.put<SubscriptionPlan>(`${this.url}/plans/${encodeURIComponent(id)}`, body);
  }

  setPlanActive(id: string, active: boolean): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(`${this.url}/plans/${encodeURIComponent(id)}/${active ? 'activate' : 'deactivate'}`, {});
  }

  // ---- Plan features
  /** `GET /api/billing/plans/comparison` (readable by any signed-in user). */
  comparison(): Observable<PlanComparison> {
    return this.http.get<PlanComparison>(this.config.getEndpointFor('api/billing/plans/comparison'));
  }

  createFeature(body: FeatureRequest): Observable<PlanFeatureDef> {
    return this.http.post<PlanFeatureDef>(`${this.url}/features`, body);
  }

  updateFeature(id: string, body: FeatureRequest): Observable<PlanFeatureDef> {
    return this.http.put<PlanFeatureDef>(`${this.url}/features/${encodeURIComponent(id)}`, body);
  }

  deleteFeature(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/features/${encodeURIComponent(id)}`);
  }

  /** Replaces what a plan includes. */
  setPlanFeatures(planId: string, items: PlanFeatureCell[]): Observable<void> {
    return this.http.put<void>(`${this.url}/plans/${encodeURIComponent(planId)}/features`, items);
  }

  // ---- Coupons
  createCoupon(body: CreateCouponRequest): Observable<Coupon> {
    return this.http.post<Coupon>(`${this.url}/coupons`, body);
  }

  deactivateCoupon(id: string): Observable<Coupon> {
    return this.http.post<Coupon>(`${this.url}/coupons/${encodeURIComponent(id)}/deactivate`, {});
  }

  deleteCoupon(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/coupons/${encodeURIComponent(id)}`);
  }

  // ---- Subscriptions
  updateSubscription(id: string, body: UpdateSubscriptionRequest): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.url}/subscriptions/${encodeURIComponent(id)}`, body);
  }

  cancelSubscription(id: string, immediate: boolean): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.url}/subscriptions/${encodeURIComponent(id)}/cancel`, null, {
      params: createRequestOption({ immediate }),
    });
  }

  // ---- Invoices
  /** Any customer's invoice as a PDF document. */
  invoicePdf(id: string): Observable<Blob> {
    return this.http.get(`${this.url}/invoices/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' });
  }

  markInvoicePaid(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/invoices/${encodeURIComponent(id)}/pay`, {});
  }

  markInvoiceOverdue(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/invoices/${encodeURIComponent(id)}/mark-overdue`, {});
  }

  /** Unpaid invoices only; kept on record as CANCELLED. */
  voidInvoice(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.url}/invoices/${encodeURIComponent(id)}/void`, {});
  }
}
