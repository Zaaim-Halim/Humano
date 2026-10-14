package com.humano.web.rest.billing;

import com.humano.dto.billing.requests.CreateCouponRequest;
import com.humano.dto.billing.requests.CreateFeatureRequest;
import com.humano.dto.billing.requests.CreateInvoiceRequest;
import com.humano.dto.billing.requests.CreateSubscriptionPlanRequest;
import com.humano.dto.billing.requests.CreateSubscriptionRequest;
import com.humano.dto.billing.requests.PlanFeatureItem;
import com.humano.dto.billing.requests.UpdateSubscriptionPlanRequest;
import com.humano.dto.billing.requests.UpdateSubscriptionRequest;
import com.humano.dto.billing.responses.CouponResponse;
import com.humano.dto.billing.responses.FeatureResponse;
import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.dto.billing.responses.PaymentResponse;
import com.humano.dto.billing.responses.SubscriptionPlanResponse;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.CouponService;
import com.humano.service.billing.FeatureService;
import com.humano.service.billing.InvoiceService;
import com.humano.service.billing.PaymentService;
import com.humano.service.billing.SubscriptionPlanService;
import com.humano.service.billing.SubscriptionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform (SaaS owner) billing management: plans, coupons, subscriptions, invoices and payments
 * across every customer. These records live in the master database and are shared by all tenants,
 * so they are managed only from the platform realm ({@code /api/platform/**} always runs in the
 * platform tenant) with {@code MANAGE_PLATFORM_BILLING}. Customers keep read-only access to their
 * own subscription, invoices and payments on the {@code /api/billing/**} resources.
 */
@RestController
@RequestMapping("/api/platform/billing")
@RequirePermission(PermissionsConstants.MANAGE_PLATFORM_BILLING)
public class PlatformBillingAdminResource {

    private final SubscriptionPlanService planService;
    private final CouponService couponService;
    private final SubscriptionService subscriptionService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final FeatureService featureService;

    public PlatformBillingAdminResource(
        SubscriptionPlanService planService,
        CouponService couponService,
        SubscriptionService subscriptionService,
        InvoiceService invoiceService,
        PaymentService paymentService,
        FeatureService featureService
    ) {
        this.featureService = featureService;
        this.planService = planService;
        this.couponService = couponService;
        this.subscriptionService = subscriptionService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    // ---- Plans

    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlanResponse> createPlan(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        SubscriptionPlanResponse created = planService.createSubscriptionPlan(request);
        return ResponseEntity.created(URI.create("/api/billing/plans/" + created.id())).body(created);
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<SubscriptionPlanResponse> updatePlan(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateSubscriptionPlanRequest request
    ) {
        return ResponseEntity.ok(planService.updateSubscriptionPlan(id, request));
    }

    @PostMapping("/plans/{id}/activate")
    public ResponseEntity<SubscriptionPlanResponse> activatePlan(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.activateSubscriptionPlan(id));
    }

    @PostMapping("/plans/{id}/deactivate")
    public ResponseEntity<SubscriptionPlanResponse> deactivatePlan(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.deactivateSubscriptionPlan(id));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        planService.deleteSubscriptionPlan(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Plan features (catalog + what each plan includes)

    @PostMapping("/features")
    public ResponseEntity<FeatureResponse> createFeature(@Valid @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.ok(featureService.createFeature(request));
    }

    @PutMapping("/features/{id}")
    public ResponseEntity<FeatureResponse> updateFeature(@PathVariable UUID id, @Valid @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.ok(featureService.updateFeature(id, request));
    }

    @DeleteMapping("/features/{id}")
    public ResponseEntity<Void> deleteFeature(@PathVariable UUID id) {
        featureService.deleteFeature(id);
        return ResponseEntity.noContent().build();
    }

    /** Replaces what a plan includes. */
    @PutMapping("/plans/{id}/features")
    public ResponseEntity<Void> setPlanFeatures(@PathVariable UUID id, @Valid @RequestBody List<PlanFeatureItem> items) {
        featureService.setPlanFeatures(id, items);
        return ResponseEntity.noContent().build();
    }

    // ---- Coupons

    @GetMapping("/coupons")
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_BILLING)
    public ResponseEntity<Page<CouponResponse>> listCoupons(Pageable pageable) {
        return ResponseEntity.ok(couponService.getAllCoupons(pageable));
    }

    @PostMapping("/coupons")
    public ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        CouponResponse created = couponService.createCoupon(request);
        return ResponseEntity.created(URI.create("/api/platform/billing/coupons/" + created.id())).body(created);
    }

    @PostMapping("/coupons/{id}/deactivate")
    public ResponseEntity<CouponResponse> deactivateCoupon(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.deactivateCoupon(id));
    }

    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable UUID id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Subscriptions (any tenant: the request names it)

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResponse> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionResponse created = subscriptionService.createSubscription(request);
        return ResponseEntity.created(URI.create("/api/billing/subscriptions/" + created.id())).body(created);
    }

    @PutMapping("/subscriptions/{id}")
    public ResponseEntity<SubscriptionResponse> updateSubscription(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateSubscriptionRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.updateSubscription(id, request));
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "false") boolean immediate
    ) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(id, immediate));
    }

    // ---- Invoices

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponse created = invoiceService.createInvoice(request);
        return ResponseEntity.created(URI.create("/api/billing/invoices/" + created.id())).body(created);
    }

    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<InvoiceResponse> markInvoicePaid(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.markAsPaid(id));
    }

    @PostMapping("/invoices/{id}/mark-overdue")
    public ResponseEntity<InvoiceResponse> markInvoiceOverdue(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.markAsOverdue(id));
    }

    /** Void an unpaid invoice (kept on record as cancelled; paid invoices are refunded, not voided). */
    @PostMapping("/invoices/{id}/void")
    public ResponseEntity<InvoiceResponse> voidInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.voidInvoice(id));
    }

    // ---- Payments

    @PostMapping("/payments/{id}/complete")
    public ResponseEntity<PaymentResponse> completePayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.completePayment(id));
    }

    @PostMapping("/payments/{id}/fail")
    public ResponseEntity<PaymentResponse> failPayment(@PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(paymentService.failPayment(id, reason));
    }

    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable UUID id, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(paymentService.refundPayment(id, amount));
    }
}
