package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateWorkPermitRequest;
import com.humano.dto.hr.requests.UpdateWorkPermitRequest;
import com.humano.dto.hr.responses.WorkPermitResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.WorkPermitService;
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
 * REST controller for managing WorkPermit records.
 */
@RestController
@RequestMapping("/api/hr/work-permits")
public class WorkPermitResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkPermitResource.class);
    private static final String ENTITY_NAME = "workPermit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final WorkPermitService service;

    public WorkPermitResource(WorkPermitService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<WorkPermitResponse> create(@Valid @RequestBody CreateWorkPermitRequest request) throws URISyntaxException {
        LOG.debug("REST request to create WorkPermit: {}", request);
        WorkPermitResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/work-permits/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<WorkPermitResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkPermitRequest request) {
        LOG.debug("REST request to update WorkPermit: {}", id);
        WorkPermitResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<WorkPermitResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<WorkPermitResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete WorkPermit: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
