package com.humano.web.rest.tenant;

import com.humano.config.multitenancy.TenantContext;
import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.dto.tenant.requests.CreateOrganizationRequest;
import com.humano.dto.tenant.requests.CreateTenantStorageConfigRequest;
import com.humano.dto.tenant.requests.UpdateOrganizationRequest;
import com.humano.dto.tenant.requests.UpdateTenantRequest;
import com.humano.dto.tenant.responses.OrganizationResponse;
import com.humano.dto.tenant.responses.TenantResponse;
import com.humano.dto.tenant.responses.TenantStorageConfigResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.tenant.OrganizationService;
import com.humano.service.tenant.TenantService;
import com.humano.service.tenant.TenantStorageConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant self-service REST endpoints (ROADMAP P2.3). Operates against master DB rows that
 * describe the current tenant — never against the tenant's own DB. The tenant is identified
 * via {@link TenantContext} which {@code TenantResolutionFilter} populates from the request
 * subdomain or {@code X-Tenant-ID} header.
 *
 * <p>Reads are open to any authenticated user belonging to the tenant; mutations require
 * {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/tenant")
@PreAuthorize("isAuthenticated()")
public class TenantResource {

    private static final Logger LOG = LoggerFactory.getLogger(TenantResource.class);

    private final TenantService tenantService;
    private final OrganizationService organizationService;
    private final TenantStorageConfigService storageConfigService;
    private final TenantIdResolver tenantIdResolver;

    public TenantResource(
        TenantService tenantService,
        OrganizationService organizationService,
        TenantStorageConfigService storageConfigService,
        TenantIdResolver tenantIdResolver
    ) {
        this.tenantService = tenantService;
        this.organizationService = organizationService;
        this.storageConfigService = storageConfigService;
        this.tenantIdResolver = tenantIdResolver;
    }

    // ---- /me ---------------------------------------------------------------------------

    @GetMapping("/me")
    public TenantResponse getCurrent() {
        return tenantService.getTenantById(currentTenantId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public TenantResponse updateCurrent(@Valid @RequestBody UpdateTenantRequest request) {
        LOG.info("Tenant '{}' self-update", TenantContext.getCurrentTenant());
        return tenantService.updateTenant(currentTenantId(), request);
    }

    // ---- /organizations ----------------------------------------------------------------

    @GetMapping("/organizations")
    public List<OrganizationResponse> listOrganizations() {
        return organizationService.getOrganizationsByTenant(currentTenantId());
    }

    @PostMapping("/organizations")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        // Ignore any caller-supplied tenantId — always create under the current tenant.
        CreateOrganizationRequest scoped = new CreateOrganizationRequest(request.name(), currentTenantId());
        return organizationService.createOrganization(scoped);
    }

    @PutMapping("/organizations/{id}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public OrganizationResponse updateOrganization(@PathVariable("id") UUID id, @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateOrganization(id, request);
    }

    @DeleteMapping("/organizations/{id}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteOrganization(@PathVariable("id") UUID id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    // ---- /storage-configs --------------------------------------------------------------

    @GetMapping("/storage-configs/active")
    public TenantStorageConfigResponse getActiveStorageConfig() {
        return storageConfigService.getActiveStorageConfigByTenant(currentTenantId());
    }

    @PostMapping("/storage-configs")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantStorageConfigResponse createStorageConfig(@Valid @RequestBody CreateTenantStorageConfigRequest request) {
        // Scope to the current tenant regardless of payload.
        CreateTenantStorageConfigRequest scoped = new CreateTenantStorageConfigRequest(
            currentTenantId(),
            request.storageBackend(),
            request.maxFileSizeMb(),
            request.maxStorageGb(),
            request.filesystemPath(),
            request.s3Bucket(),
            request.s3Region(),
            request.s3AccessKey(),
            request.s3SecretKey(),
            request.azureAccountName(),
            request.azureContainer(),
            request.azureConnectionString()
        );
        return storageConfigService.createStorageConfig(scoped);
    }

    @PostMapping("/storage-configs/{id}/activate")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public TenantStorageConfigResponse activateStorageConfig(@PathVariable("id") UUID id) {
        return storageConfigService.activateStorageConfig(id);
    }

    @PostMapping("/storage-configs/{id}/deactivate")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public TenantStorageConfigResponse deactivateStorageConfig(@PathVariable("id") UUID id) {
        return storageConfigService.deactivateStorageConfig(id);
    }

    @DeleteMapping("/storage-configs/{id}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteStorageConfig(@PathVariable("id") UUID id) {
        storageConfigService.deleteStorageConfig(id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentTenantId() {
        return tenantIdResolver.requireCurrentTenantId();
    }
}
