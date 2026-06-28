package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateReferenceDataRequest;
import com.humano.dto.hr.requests.UpdateReferenceDataRequest;
import com.humano.dto.hr.responses.ReferenceDataResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmploymentTypeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing EmploymentType reference data.
 */
@RestController
@RequestMapping("/api/hr/employment-types")
public class EmploymentTypeResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmploymentTypeResource.class);
    private static final String ENTITY_NAME = "employmentType";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmploymentTypeService service;

    public EmploymentTypeResource(EmploymentTypeService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.MANAGE_ATTENDANCE)
    public ResponseEntity<ReferenceDataResponse> create(@Valid @RequestBody CreateReferenceDataRequest request) throws URISyntaxException {
        LOG.debug("REST request to create EmploymentType: {}", request);
        ReferenceDataResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employment-types/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_ATTENDANCE)
    public ResponseEntity<ReferenceDataResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateReferenceDataRequest request) {
        LOG.debug("REST request to update EmploymentType: {}", id);
        ReferenceDataResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping
    @RequirePermission(PermissionsConstants.MANAGE_ATTENDANCE)
    public ResponseEntity<Page<ReferenceDataResponse>> getAll(Pageable pageable) {
        Page<ReferenceDataResponse> page = service.getAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_ATTENDANCE)
    public ResponseEntity<ReferenceDataResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.MANAGE_ATTENDANCE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmploymentType: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
