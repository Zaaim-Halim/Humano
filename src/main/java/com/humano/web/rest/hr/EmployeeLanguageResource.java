package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeLanguageRequest;
import com.humano.dto.hr.requests.UpdateEmployeeLanguageRequest;
import com.humano.dto.hr.responses.EmployeeLanguageResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeLanguageService;
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
 * REST controller for managing EmployeeLanguage records.
 */
@RestController
@RequestMapping("/api/hr/employee-languages")
public class EmployeeLanguageResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeLanguageResource.class);
    private static final String ENTITY_NAME = "employeeLanguage";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeLanguageService service;

    public EmployeeLanguageResource(EmployeeLanguageService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeLanguageResponse> create(@Valid @RequestBody CreateEmployeeLanguageRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeLanguage: {}", request);
        EmployeeLanguageResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-languages/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeLanguageResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeLanguageRequest request
    ) {
        LOG.debug("REST request to update EmployeeLanguage: {}", id);
        EmployeeLanguageResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeLanguageResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeLanguageResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeLanguage: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
