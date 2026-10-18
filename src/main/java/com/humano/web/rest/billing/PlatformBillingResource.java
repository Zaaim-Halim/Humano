package com.humano.web.rest.billing;

import com.humano.dto.billing.responses.AddonRowResponse;
import com.humano.dto.billing.responses.CustomerStorageResponse;
import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.dto.billing.responses.PaymentResponse;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.InvoiceDocumentService;
import com.humano.service.billing.InvoiceService;
import com.humano.service.billing.PaymentService;
import com.humano.service.billing.SubscriptionService;
import com.humano.service.billing.entitlement.StorageAddonService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cross-tenant billing reads for platform admins. Lives under
 * {@code /api/platform/billing/**} and is gated by the {@code VIEW_PLATFORM_BILLING}
 * permission (held by platform roles seeded only in the platform tenant).
 * No mutations here — tenant-scoped writes stay on the per-tenant resources to keep ownership checks consistent.
 */
@RestController
@RequestMapping("/api/platform/billing")
@RequirePermission(PermissionsConstants.VIEW_PLATFORM_BILLING)
public class PlatformBillingResource {

    private final SubscriptionService subscriptionService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    private final StorageAddonService storageAddonService;
    private final InvoiceDocumentService invoiceDocumentService;

    public PlatformBillingResource(
        SubscriptionService subscriptionService,
        InvoiceService invoiceService,
        PaymentService paymentService,
        StorageAddonService storageAddonService,
        InvoiceDocumentService invoiceDocumentService
    ) {
        this.storageAddonService = storageAddonService;
        this.invoiceDocumentService = invoiceDocumentService;
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

    /** Every customer's add-ons (extra storage), newest first. */
    @GetMapping("/addons")
    public List<AddonRowResponse> listAddons() {
        return storageAddonService.listAll();
    }

    /** A customer's storage: extra packs held, the plan's limit with them, and what is used. */
    @GetMapping("/addons/storage/by-tenant/{tenantId}")
    public CustomerStorageResponse getCustomerStorage(@PathVariable UUID tenantId) {
        return storageAddonService.customerStorage(tenantId);
    }

    @GetMapping("/invoices")
    public ResponseEntity<Page<InvoiceResponse>> listInvoices(Pageable pageable) {
        return ResponseEntity.ok(invoiceService.getAllInvoices(pageable));
    }

    /** Any customer's invoice as a PDF document. */
    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable UUID id) {
        return InvoiceResource.pdfResponse(invoiceDocumentService.pdf(id, null));
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
