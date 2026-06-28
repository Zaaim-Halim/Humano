package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeLicenseRequest;
import com.humano.dto.hr.requests.UpdateEmployeeLicenseRequest;
import com.humano.dto.hr.responses.EmployeeLicenseResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeLicenseService;
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
 * REST controller for managing EmployeeLicense records.
 */
@RestController
@RequestMapping("/api/hr/employee-licenses")
public class EmployeeLicenseResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeLicenseResource.class);
    private static final String ENTITY_NAME = "employeeLicense";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeLicenseService service;

    public EmployeeLicenseResource(EmployeeLicenseService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeLicenseResponse> create(@Valid @RequestBody CreateEmployeeLicenseRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeLicense: {}", request);
        EmployeeLicenseResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-licenses/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeLicenseResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeLicenseRequest request) {
        LOG.debug("REST request to update EmployeeLicense: {}", id);
        EmployeeLicenseResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeLicenseResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeLicenseResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeLicense: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
