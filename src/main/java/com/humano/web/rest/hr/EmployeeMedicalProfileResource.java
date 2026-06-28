package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeMedicalProfileRequest;
import com.humano.dto.hr.requests.UpdateEmployeeMedicalProfileRequest;
import com.humano.dto.hr.responses.EmployeeMedicalProfileResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeMedicalProfileService;
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
 * REST controller for managing EmployeeMedicalProfile records.
 */
@RestController
@RequestMapping("/api/hr/employee-medical-profiles")
public class EmployeeMedicalProfileResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeMedicalProfileResource.class);
    private static final String ENTITY_NAME = "employeeMedicalProfile";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeMedicalProfileService service;

    public EmployeeMedicalProfileResource(EmployeeMedicalProfileService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeMedicalProfileResponse> create(@Valid @RequestBody CreateEmployeeMedicalProfileRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeMedicalProfile: {}", request);
        EmployeeMedicalProfileResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-medical-profiles/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeMedicalProfileResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeMedicalProfileRequest request
    ) {
        LOG.debug("REST request to update EmployeeMedicalProfile: {}", id);
        EmployeeMedicalProfileResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeMedicalProfileResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeMedicalProfileResponse> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeMedicalProfile: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
