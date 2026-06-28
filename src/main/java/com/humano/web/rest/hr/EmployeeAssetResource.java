package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeAssetRequest;
import com.humano.dto.hr.requests.UpdateEmployeeAssetRequest;
import com.humano.dto.hr.responses.EmployeeAssetResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeAssetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing EmployeeAsset records.
 */
@RestController
@RequestMapping("/api/hr/employee-assets")
public class EmployeeAssetResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeAssetResource.class);
    private static final String ENTITY_NAME = "employeeAsset";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeAssetService service;

    public EmployeeAssetResource(EmployeeAssetService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeAssetResponse> create(@Valid @RequestBody CreateEmployeeAssetRequest request) throws URISyntaxException {
        LOG.debug("REST request to create EmployeeAsset: {}", request);
        EmployeeAssetResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-assets/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeAssetResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeAssetRequest request) {
        LOG.debug("REST request to update EmployeeAsset: {}", id);
        EmployeeAssetResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeAssetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeAssetResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeAsset: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
