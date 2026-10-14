package com.humano.web.rest.billing;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.billing.requests.CreateInvoiceRequest;
import com.humano.dto.billing.responses.InvoiceResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.InvoiceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Invoices for the current tenant. Every endpoint scopes to the resolved current
 * tenant; cross-tenant listing lives at {@link PlatformBillingResource}.
 */
@RestController
@RequestMapping("/api/billing/invoices")
public class InvoiceResource {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceResource.class);

    private final InvoiceService invoiceService;
    private final TenantIdResolver tenantIdResolver;

    public InvoiceResource(InvoiceService invoiceService, TenantIdResolver tenantIdResolver) {
        this.invoiceService = invoiceService;
        this.tenantIdResolver = tenantIdResolver;
    }

    @GetMapping
    @RequirePermission(PermissionsConstants.VIEW_INVOICE)
    public ResponseEntity<List<InvoiceResponse>> list() {
        UUID tenantId = tenantIdResolver.requireCurrentTenantId();
        return ResponseEntity.ok(invoiceService.getInvoicesByTenant(tenantId));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.VIEW_INVOICE)
    public ResponseEntity<InvoiceResponse> get(@PathVariable UUID id) {
        InvoiceResponse invoice = invoiceService.getInvoiceById(id);
        verifyTenantOwnership(invoice);
        return ResponseEntity.ok(invoice);
    }

    private void verifyTenantOwnership(InvoiceResponse invoice) {
        UUID currentTenantId = tenantIdResolver.requireCurrentTenantId();
        if (!currentTenantId.equals(invoice.tenantId())) {
            throw new AccessDeniedException("Invoice does not belong to the current tenant");
        }
    }
}
