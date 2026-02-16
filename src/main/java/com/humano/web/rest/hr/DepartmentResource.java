package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateDepartmentRequest;
import com.humano.dto.hr.requests.UpdateDepartmentRequest;
import com.humano.dto.hr.responses.DepartmentResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.DepartmentService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing departments.
 */
@RestController
@RequestMapping("/api/hr/departments")
public class DepartmentResource {

    private static final Logger LOG = LoggerFactory.getLogger(DepartmentResource.class);
    private static final String ENTITY_NAME = "department";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DepartmentService departmentService;

    public DepartmentResource(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * {@code POST  /departments} : Create a new department.
     *
     * @param request the department creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new department
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody CreateDepartmentRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create Department: {}", request);

        DepartmentResponse result = departmentService.createDepartment(request);

        return ResponseEntity.created(new URI("/api/hr/departments/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /departments/{id}} : Updates an existing department.
     *
     * @param id the ID of the department to update
     * @param request the department update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated department
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<DepartmentResponse> updateDepartment(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        LOG.debug("REST request to update Department: {}", id);

        DepartmentResponse result = departmentService.updateDepartment(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /departments} : Get all departments with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of departments in body
     */
    @GetMapping
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<DepartmentResponse>> getAllDepartments(Pageable pageable) {
        LOG.debug("REST request to get all Departments");

        Page<DepartmentResponse> page = departmentService.getAllDepartments(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /departments/{id}} : Get department by ID.
     *
     * @param id the ID of the department to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the department
     */
    @GetMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<DepartmentResponse> getDepartment(@PathVariable UUID id) {
        LOG.debug("REST request to get Department: {}", id);

        DepartmentResponse result = departmentService.getDepartmentById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /departments/exists} : Check if department exists by name.
     *
     * @param name the name to check
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and boolean result
     */
    @GetMapping("/exists")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Boolean> existsByName(@RequestParam String name) {
        LOG.debug("REST request to check if Department exists by name: {}", name);

        boolean exists = departmentService.existsByName(name);

        return ResponseEntity.ok(exists);
    }

    /**
     * {@code DELETE  /departments/{id}} : Delete department by ID.
     *
     * @param id the ID of the department to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
        LOG.debug("REST request to delete Department: {}", id);

        departmentService.deleteDepartment(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
