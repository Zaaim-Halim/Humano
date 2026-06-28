package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeSignatureRequest;
import com.humano.dto.hr.requests.UpdateEmployeeSignatureRequest;
import com.humano.dto.hr.responses.EmployeeSignatureResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeSignatureService;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing EmployeeSignature records.
 */
@RestController
@RequestMapping("/api/hr/employee-signatures")
public class EmployeeSignatureResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeSignatureResource.class);
    private static final String ENTITY_NAME = "employeeSignature";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeSignatureService service;

    public EmployeeSignatureResource(EmployeeSignatureService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeSignatureResponse> create(@Valid @RequestBody CreateEmployeeSignatureRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeSignature: {}", request);
        EmployeeSignatureResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-signatures/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeSignatureResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeSignatureRequest request
    ) {
        LOG.debug("REST request to update EmployeeSignature: {}", id);
        EmployeeSignatureResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeSignatureResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeSignatureResponse> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeSignature: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
