package com.humano.web.rest.demo;

import com.humano.domain.enumeration.tenant.DemoRequestStatus;
import com.humano.dto.demo.ConvertDemoRequest;
import com.humano.dto.demo.CreateDemoWorkspaceRequest;
import com.humano.dto.demo.DemoCredentials;
import com.humano.dto.demo.DemoRequestResponse;
import com.humano.dto.demo.DemoWorkspaceCreated;
import com.humano.dto.demo.DemoWorkspaceResponse;
import com.humano.dto.demo.ExtendDemoRequest;
import com.humano.dto.demo.UpdateDemoRequestRequest;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.demo.DemoRequestService;
import com.humano.service.demo.DemoWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner console — demos: the requests received from the public site and the demo workspaces set
 * up for prospects (create, retry a failed setup, reset the password, extend, expire, convert to a
 * customer, delete).
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformDemoResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformDemoResource.class);

    private final DemoWorkspaceService demoService;
    private final DemoRequestService requestService;

    public PlatformDemoResource(DemoWorkspaceService demoService, DemoRequestService requestService) {
        this.demoService = demoService;
        this.requestService = requestService;
    }

    // ---- Demo requests

    @GetMapping("/demo-requests")
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_TENANTS)
    public Page<DemoRequestResponse> listRequests(@RequestParam(required = false) DemoRequestStatus status, Pageable pageable) {
        return requestService.list(status, pageable);
    }

    @PatchMapping("/demo-requests/{id}")
    @RequirePermission(PermissionsConstants.PROVISION_TENANT)
    public DemoRequestResponse updateRequest(@PathVariable UUID id, @Valid @RequestBody UpdateDemoRequestRequest request) {
        return requestService.update(id, request);
    }

    @DeleteMapping("/demo-requests/{id}")
    @RequirePermission(PermissionsConstants.PROVISION_TENANT)
    public ResponseEntity<Void> deleteRequest(@PathVariable UUID id) {
        requestService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Demo workspaces

    @GetMapping("/demos")
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_TENANTS)
    public List<DemoWorkspaceResponse> list() {
        return demoService.list();
    }

    /** Accepted, not created: the workspace is set up in the background (poll {@code setup}). */
    @PostMapping("/demos")
    @RequirePermission(PermissionsConstants.PROVISION_TENANT)
    public ResponseEntity<DemoWorkspaceCreated> create(@Valid @RequestBody CreateDemoWorkspaceRequest request) {
        LOG.info("REST request to create demo workspace {}", request.subdomain());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(demoService.create(request));
    }

    @PostMapping("/demos/{id}/retry")
    @RequirePermission(PermissionsConstants.PROVISION_TENANT)
    public ResponseEntity<DemoWorkspaceCreated> retry(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(demoService.retrySetup(id));
    }

    @PostMapping("/demos/{id}/reset-password")
    @RequirePermission(PermissionsConstants.PROVISION_TENANT)
    public DemoCredentials resetPassword(@PathVariable UUID id) {
        return demoService.resetPassword(id);
    }

    @PostMapping("/demos/{id}/extend")
    @RequirePermission(PermissionsConstants.SUSPEND_TENANT)
    public DemoWorkspaceResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendDemoRequest request) {
        return demoService.extend(id, request);
    }

    @PostMapping("/demos/{id}/expire")
    @RequirePermission(PermissionsConstants.SUSPEND_TENANT)
    public DemoWorkspaceResponse expire(@PathVariable UUID id) {
        return demoService.expire(id);
    }

    @PostMapping("/demos/{id}/convert")
    @RequirePermission(PermissionsConstants.MANAGE_PLATFORM_BILLING)
    public ResponseEntity<Void> convert(@PathVariable UUID id, @Valid @RequestBody ConvertDemoRequest request) {
        LOG.info("REST request to convert demo {} to a customer", id);
        demoService.convert(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/demos/{id}")
    @RequirePermission(PermissionsConstants.DEPROVISION_TENANT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.warn("REST request to delete demo workspace {}", id);
        demoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
