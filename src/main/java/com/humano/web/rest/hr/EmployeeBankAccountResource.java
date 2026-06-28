package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeBankAccountRequest;
import com.humano.dto.hr.requests.UpdateEmployeeBankAccountRequest;
import com.humano.dto.hr.responses.EmployeeBankAccountResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeBankAccountService;
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
 * REST controller for managing EmployeeBankAccount records.
 */
@RestController
@RequestMapping("/api/hr/employee-bank-accounts")
public class EmployeeBankAccountResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeBankAccountResource.class);
    private static final String ENTITY_NAME = "employeeBankAccount";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeBankAccountService service;

    public EmployeeBankAccountResource(EmployeeBankAccountService service) {
        this.service = service;
    }

    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeBankAccountResponse> create(@Valid @RequestBody CreateEmployeeBankAccountRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeBankAccount: {}", request);
        EmployeeBankAccountResponse result = service.create(request);
        return ResponseEntity.created(new URI("/api/hr/employee-bank-accounts/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeBankAccountResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeBankAccountRequest request
    ) {
        LOG.debug("REST request to update EmployeeBankAccount: {}", id);
        EmployeeBankAccountResponse result = service.update(id, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeBankAccountResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeBankAccountResponse>> getByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.getByEmployeeId(employeeId));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeBankAccount: {}", id);
        service.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
