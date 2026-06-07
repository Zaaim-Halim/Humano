package com.humano.web.rest.billing;

import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.dto.billing.responses.PaymentResponse;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.security.annotation.RequireAdmin;
import com.humano.service.billing.InvoiceService;
import com.humano.service.billing.PaymentService;
import com.humano.service.billing.SubscriptionService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cross-tenant billing reads for platform admins. Lives under
 * {@code /api/platform/billing/**} and is gated by {@code ROLE_ADMIN}.
 * No mutations here — tenant-scoped writes stay on the per-tenant resources to keep ownership checks consistent.
 */
@RestController
@RequestMapping("/api/platform/billing")
@RequireAdmin
public class PlatformBillingResource {

    private final SubscriptionService subscriptionService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    public PlatformBillingResource(SubscriptionService subscriptionService, InvoiceService invoiceService, PaymentService paymentService) {
        this.subscriptionService = subscriptionService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<Page<SubscriptionResponse>> listSubscriptions(Pageable pageable) {
        return ResponseEntity.ok(subscriptionService.getAllSubscriptions(pageable));
    }

    @GetMapping("/subscriptions/by-tenant/{tenantId}")
    public ResponseEntity<SubscriptionResponse> getSubscriptionByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionByTenant(tenantId));
    }

    @GetMapping("/invoices")
    public ResponseEntity<Page<InvoiceResponse>> listInvoices(Pageable pageable) {
        return ResponseEntity.ok(invoiceService.getAllInvoices(pageable));
    }

    @GetMapping("/invoices/by-tenant/{tenantId}")
    public ResponseEntity<List<InvoiceResponse>> listInvoicesByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByTenant(tenantId));
    }

    @GetMapping("/payments")
    public ResponseEntity<Page<PaymentResponse>> listPayments(Pageable pageable) {
        return ResponseEntity.ok(paymentService.getAllPayments(pageable));
    }
}
