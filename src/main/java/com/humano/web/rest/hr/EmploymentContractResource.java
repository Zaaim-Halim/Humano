package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmploymentContractRequest;
import com.humano.dto.hr.requests.UpdateEmploymentContractRequest;
import com.humano.dto.hr.responses.EmploymentContractResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmploymentContractService;
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
 * REST controller for managing EmploymentContract records.
 */
@RestController
@RequestMapping("/api/hr/employment-contracts")
public class EmploymentContractResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmploymentContractResource.class);
    private static final String ENTITY_NAME = "employmentContract";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmploymentContractService service;

    public EmploymentContractResource(EmploymentContractService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmploymentContractResponse> create(@Valid @RequestBody CreateEmploymentContractRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmploymentContract: {}", request);
        EmploymentContractResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employment-contracts/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmploymentContractResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmploymentContractRequest request
    ) {
        LOG.debug("REST request to update EmploymentContract: {}", id);
        EmploymentContractResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmploymentContractResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmploymentContractResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmploymentContract: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
