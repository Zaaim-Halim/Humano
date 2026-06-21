package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.UpdateOrganizationSettingsRequest;
import com.humano.dto.payroll.response.OrganizationSettingsResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.payroll.OrganizationSettingsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the company-level payroll policy ({@code /api/org-settings}).
 * A tenant-scoped singleton — read returns the saved settings or transient
 * defaults; the PUT upserts the single row.
 *
 * <p>Gated on {@code CONFIGURE_TENANT_SETTINGS} (held by {@code ROLE_TENANT_ADMIN}).
 * The roadmap suggested {@code SYSTEM_CONFIGURATION}, but that permission is
 * granted to no role in {@code DefaultRolePermissions}, so it would 403 for every
 * caller; {@code CONFIGURE_TENANT_SETTINGS} is the wired, semantically-equivalent
 * gate for configuring a tenant/company's settings.
 */
@RestController
@RequestMapping("/api/org-settings")
@RequirePermission(PermissionsConstants.CONFIGURE_TENANT_SETTINGS)
public class OrganizationSettingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationSettingsResource.class);

    private final OrganizationSettingsService service;

    public OrganizationSettingsResource(OrganizationSettingsService service) {
        this.service = service;
    }

    /** {@code GET /api/org-settings} : current company payroll policy (or defaults). */
    @GetMapping
    public ResponseEntity<OrganizationSettingsResponse> get() {
        LOG.debug("REST request to get OrganizationSettings");
        return ResponseEntity.ok(service.get());
    }

    /** {@code PUT /api/org-settings} : replace the company payroll policy. */
    @PutMapping
    public ResponseEntity<OrganizationSettingsResponse> update(@Valid @RequestBody UpdateOrganizationSettingsRequest request) {
        LOG.debug("REST request to update OrganizationSettings: {}", request);
        return ResponseEntity.ok(service.update(request));
    }
}
