package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.UpdateEmployeeAttributesRequest;
import com.humano.dto.hr.responses.EmployeeAttributeResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeeAttributeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for an employee's custom key/value attributes, nested under
 * the employee resource. Reads are gated on {@code READ_EMPLOYEE}; the full
 * replace is gated on {@code UPDATE_EMPLOYEE}, mirroring the employee profile.
 */
@RestController
@RequestMapping("/api/hr/employees/{employeeId}/attributes")
public class EmployeeAttributeResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeAttributeResource.class);
    private static final String ENTITY_NAME = "employeeAttribute";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeAttributeService employeeAttributeService;

    public EmployeeAttributeResource(EmployeeAttributeService employeeAttributeService) {
        this.employeeAttributeService = employeeAttributeService;
    }

    /**
     * {@code GET  /api/hr/employees/{employeeId}/attributes} : List an employee's attributes.
     *
     * @param employeeId the employee id
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the attributes in body
     */
    @GetMapping
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<List<EmployeeAttributeResponse>> getAttributes(@PathVariable UUID employeeId) {
        LOG.debug("REST request to get attributes for Employee: {}", employeeId);
        return ResponseEntity.ok(employeeAttributeService.getAttributes(employeeId));
    }

    /**
     * {@code PUT  /api/hr/employees/{employeeId}/attributes} : Replace an employee's attributes.
     *
     * @param employeeId the employee id
     * @param request the complete new set of attributes
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the saved attributes in body
     */
    @PutMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<List<EmployeeAttributeResponse>> replaceAttributes(
        @PathVariable UUID employeeId,
        @Valid @RequestBody UpdateEmployeeAttributesRequest request
    ) {
        LOG.debug("REST request to replace attributes for Employee: {}", employeeId);
        List<EmployeeAttributeResponse> result = employeeAttributeService.replaceAttributes(employeeId, request);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employeeId.toString()))
            .body(result);
    }
}
