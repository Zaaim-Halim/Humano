package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeCertificationRequest;
import com.humano.dto.hr.requests.UpdateEmployeeCertificationRequest;
import com.humano.dto.hr.responses.EmployeeCertificationResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeCertificationService;
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
 * REST controller for managing EmployeeCertification records.
 */
@RestController
@RequestMapping("/api/hr/employee-certifications")
public class EmployeeCertificationResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeCertificationResource.class);
    private static final String ENTITY_NAME = "employeeCertification";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeCertificationService service;

    public EmployeeCertificationResource(EmployeeCertificationService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeCertificationResponse> create(@Valid @RequestBody CreateEmployeeCertificationRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeCertification: {}", request);
        EmployeeCertificationResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-certifications/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeCertificationResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeCertificationRequest request
    ) {
        LOG.debug("REST request to update EmployeeCertification: {}", id);
        EmployeeCertificationResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeCertificationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeCertificationResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeCertification: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
