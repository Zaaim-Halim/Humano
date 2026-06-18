package com.humano.web.rest.billing;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.billing.requests.CreatePaymentRequest;
import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.dto.billing.responses.PaymentResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.InvoiceService;
import com.humano.service.billing.PaymentService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Payments for the current tenant. Payments are scoped indirectly via their parent
 * invoice — we resolve the invoice first to verify it belongs to the current tenant before
 * letting any mutation through.
 */
@RestController
@RequestMapping("/api/billing/payments")
@RequirePermission(PermissionsConstants.PROCESS_PAYMENTS)
public class PaymentResource {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentResource.class);

    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final TenantIdResolver tenantIdResolver;

    public PaymentResource(PaymentService paymentService, InvoiceService invoiceService, TenantIdResolver tenantIdResolver) {
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.tenantIdResolver = tenantIdResolver;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> get(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.getPaymentById(id);
        verifyTenantOwnership(payment.invoiceId());
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/by-invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponse>> listByInvoice(@PathVariable UUID invoiceId) {
        verifyTenantOwnership(invoiceId);
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(invoiceId));
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        LOG.debug("REST request to create Payment: {}", request);
        verifyTenantOwnership(request.invoiceId());
        PaymentResponse created = paymentService.createPayment(request);
        return ResponseEntity.created(URI.create("/api/billing/payments/" + created.id())).body(created);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<PaymentResponse> complete(@PathVariable UUID id) {
        verifyTenantOwnership(paymentService.getPaymentById(id).invoiceId());
        return ResponseEntity.ok(paymentService.completePayment(id));
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<PaymentResponse> fail(@PathVariable UUID id, @RequestParam(required = false) String reason) {
        verifyTenantOwnership(paymentService.getPaymentById(id).invoiceId());
        return ResponseEntity.ok(paymentService.failPayment(id, reason));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID id, @RequestParam BigDecimal amount) {
        verifyTenantOwnership(paymentService.getPaymentById(id).invoiceId());
        return ResponseEntity.ok(paymentService.refundPayment(id, amount));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<PaymentResponse> retry(@PathVariable UUID id, @RequestParam String token) {
        verifyTenantOwnership(paymentService.getPaymentById(id).invoiceId());
        return ResponseEntity.ok(paymentService.retryPayment(id, token));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        verifyTenantOwnership(paymentService.getPaymentById(id).invoiceId());
        paymentService.deletePayment(id);
        return ResponseEntity.noContent().build();
    }

    private void verifyTenantOwnership(UUID invoiceId) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(invoiceId);
        UUID currentTenantId = tenantIdResolver.requireCurrentTenantId();
        if (!currentTenantId.equals(invoice.tenantId())) {
            throw new AccessDeniedException("Payment's invoice does not belong to the current tenant");
        }
    }
}
