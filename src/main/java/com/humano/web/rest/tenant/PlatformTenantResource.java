package com.humano.web.rest.tenant;

import com.humano.config.multitenancy.TenantDataSourceProvider;
import com.humano.config.multitenancy.TenantDataSourceProvider.ConnectionPoolStats;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.dto.tenant.responses.TenantResponse;
import com.humano.repository.tenant.TenantRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.multitenancy.TenantProvisioningService;
import com.humano.service.tenant.TenantService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-admin REST endpoints for the tenant lifecycle . Always operates
 * against the master DB — {@code TenantResolutionFilter#shouldNotFilter} excludes
 * {@code /api/platform/**} so no tenant context is set.
 */
@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
public class PlatformTenantResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformTenantResource.class);

    private final TenantProvisioningService provisioningService;
    private final TenantService tenantService;
    private final TenantRepository tenantRepository;
    private final TenantDataSourceProvider dataSourceProvider;

    public PlatformTenantResource(
        TenantProvisioningService provisioningService,
        TenantService tenantService,
        TenantRepository tenantRepository,
        TenantDataSourceProvider dataSourceProvider
    ) {
        this.provisioningService = provisioningService;
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.dataSourceProvider = dataSourceProvider;
    }

    /** Provision a new tenant. Idempotent / resumable for matching subdomain . */
    @PostMapping
    public ResponseEntity<TenantResponse> provision(@Valid @RequestBody TenantRegistrationDTO request) {
        LOG.info("REST request to provision tenant subdomain={}", request.getSubdomain());
        Tenant tenant = provisioningService.provisionTenant(request);
        TenantResponse body = tenantService.toResponse(tenant);
        return ResponseEntity.created(URI.create("/api/platform/tenants/" + body.id())).body(body);
    }

    /** List tenants, optionally filtered by status. */
    @GetMapping
    public Page<TenantResponse> list(@RequestParam(required = false) TenantStatus status, Pageable pageable) {
        LOG.debug("REST request to list tenants status={} page={}", status, pageable);
        return tenantService.getTenantsByStatus(status, pageable);
    }

    /** Tenant details + live DB pool stats for the active Hikari pool (if loaded). */
    @GetMapping("/{id}")
    public TenantDetailResponse getOne(@PathVariable("id") UUID id) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("Tenant", id));
        ConnectionPoolStats stats = dataSourceProvider.getPoolStats().get(tenant.getSubdomain());
        return new TenantDetailResponse(tenantService.toResponse(tenant), stats);
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable("id") UUID id) {
        LOG.info("REST request to suspend tenant {}", id);
        provisioningService.suspendTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable("id") UUID id) {
        LOG.info("REST request to activate tenant {}", id);
        provisioningService.activateTenant(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deprovision(@PathVariable("id") UUID id) {
        LOG.warn("REST request to deprovision tenant {}", id);
        provisioningService.deprovisionTenant(id);
        return ResponseEntity.noContent().build();
    }

    /** Response payload for the single-tenant detail endpoint. */
    public record TenantDetailResponse(TenantResponse tenant, ConnectionPoolStats poolStats) {}
}
