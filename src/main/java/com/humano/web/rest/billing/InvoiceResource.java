package com.humano.web.rest.billing;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.billing.requests.CreateInvoiceRequest;
import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.billing.InvoiceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Invoices for the current tenant. Every endpoint scopes to the resolved current
 * tenant; cross-tenant listing lives at {@link PlatformBillingResource}.
 */
@RestController
@RequestMapping("/api/billing/invoices")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class InvoiceResource {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceResource.class);

    private final InvoiceService invoiceService;
    private final TenantIdResolver tenantIdResolver;

    public InvoiceResource(InvoiceService invoiceService, TenantIdResolver tenantIdResolver) {
        this.invoiceService = invoiceService;
        this.tenantIdResolver = tenantIdResolver;
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> list() {
        UUID tenantId = tenantIdResolver.requireCurrentTenantId();
        return ResponseEntity.ok(invoiceService.getInvoicesByTenant(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> get(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        verifyTenantOwnership(invoice);
        return ResponseEntity.ok(invoice);
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        LOG.debug("REST request to create Invoice: {}", request);
        UUID currentTenantId = tenantIdResolver.requireCurrentTenantId();
        CreateInvoiceRequest safe = new CreateInvoiceRequest(
            currentTenantId,
            request.subscriptionId(),
            request.invoiceNumber(),
            request.amount(),
            request.taxAmount(),
            request.dueDate(),
            request.couponCode()
        );
        InvoiceResponse created = invoiceService.createInvoice(safe);
        return ResponseEntity.created(URI.create("/api/billing/invoices/" + created.id())).body(created);
    }

    /**
     * Mark an invoice as paid. The roadmap calls this {@code POST .../pay} for client
     * ergonomics even though the underlying service operation is "mark paid".
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<InvoiceResponse> markPaid(@PathVariable UUID id) {
        verifyTenantOwnership(invoiceService.getInvoiceById(id));
        return ResponseEntity.ok(invoiceService.markAsPaid(id));
    }

    @PostMapping("/{id}/mark-overdue")
    public ResponseEntity<InvoiceResponse> markOverdue(@PathVariable UUID id) {
        verifyTenantOwnership(invoiceService.getInvoiceById(id));
        return ResponseEntity.ok(invoiceService.markAsOverdue(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        verifyTenantOwnership(invoiceService.getInvoiceById(id));
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }

    private void verifyTenantOwnership(InvoiceResponse invoice) {
        UUID currentTenantId = tenantIdResolver.requireCurrentTenantId();
        if (!currentTenantId.equals(invoice.tenantId())) {
            throw new AccessDeniedException("Invoice does not belong to the current tenant");
        }
    }
}
