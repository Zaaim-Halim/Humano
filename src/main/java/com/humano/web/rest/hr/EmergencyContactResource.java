package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmergencyContactRequest;
import com.humano.dto.hr.requests.UpdateEmergencyContactRequest;
import com.humano.dto.hr.responses.EmergencyContactResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmergencyContactService;
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
 * REST controller for managing EmergencyContact records.
 */
@RestController
@RequestMapping("/api/hr/emergency-contacts")
public class EmergencyContactResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmergencyContactResource.class);
    private static final String ENTITY_NAME = "emergencyContact";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmergencyContactService service;

    public EmergencyContactResource(EmergencyContactService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmergencyContactResponse> create(@Valid @RequestBody CreateEmergencyContactRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmergencyContact: {}", request);
        EmergencyContactResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/emergency-contacts/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmergencyContactResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmergencyContactRequest request
    ) {
        LOG.debug("REST request to update EmergencyContact: {}", id);
        EmergencyContactResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmergencyContactResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmergencyContactResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmergencyContact: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
