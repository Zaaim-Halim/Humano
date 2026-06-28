package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeExperienceRequest;
import com.humano.dto.hr.requests.UpdateEmployeeExperienceRequest;
import com.humano.dto.hr.responses.EmployeeExperienceResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeExperienceService;
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
 * REST controller for managing EmployeeExperience records.
 */
@RestController
@RequestMapping("/api/hr/employee-experiences")
public class EmployeeExperienceResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeExperienceResource.class);
    private static final String ENTITY_NAME = "employeeExperience";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeExperienceService service;

    public EmployeeExperienceResource(EmployeeExperienceService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeExperienceResponse> create(@Valid @RequestBody CreateEmployeeExperienceRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeExperience: {}", request);
        EmployeeExperienceResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-experiences/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeExperienceResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeExperienceRequest request
    ) {
        LOG.debug("REST request to update EmployeeExperience: {}", id);
        EmployeeExperienceResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeExperienceResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeExperienceResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeExperience: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
