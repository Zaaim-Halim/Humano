package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeEducationRequest;
import com.humano.dto.hr.requests.UpdateEmployeeEducationRequest;
import com.humano.dto.hr.responses.EmployeeEducationResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeEducationService;
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
 * REST controller for managing EmployeeEducation records.
 */
@RestController
@RequestMapping("/api/hr/employee-educations")
public class EmployeeEducationResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeEducationResource.class);
    private static final String ENTITY_NAME = "employeeEducation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeEducationService service;

    public EmployeeEducationResource(EmployeeEducationService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeEducationResponse> create(@Valid @RequestBody CreateEmployeeEducationRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeEducation: {}", request);
        EmployeeEducationResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-educations/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeEducationResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeEducationRequest request
    ) {
        LOG.debug("REST request to update EmployeeEducation: {}", id);
        EmployeeEducationResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeEducationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeEducationResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeEducation: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
