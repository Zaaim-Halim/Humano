package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeProfileRequest;
import com.humano.dto.hr.requests.EmployeeSearchRequest;
import com.humano.dto.hr.requests.UpdateEmployeeProfileRequest;
import com.humano.dto.hr.responses.EmployeeProfileResponse;
import com.humano.dto.hr.responses.SimpleEmployeeProfileResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.EmployeeProfileService;
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
 * REST controller for managing Employee profiles.
 * <p>
 * This class accesses the {@link com.humano.domain.hr.Employee} entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api/hr/employees")
public class EmployeeResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeResource.class);
    private static final String ENTITY_NAME = "employee";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeProfileService employeeProfileService;

    public EmployeeResource(EmployeeProfileService employeeProfileService) {
        this.employeeProfileService = employeeProfileService;
    }

    /**
     * {@code POST  /employees/{userId}} : Create a new employee profile for an existing user.
     *
     * @param userId the ID of the user to create employee profile for
     * @param request the employee profile creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new employee
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<EmployeeProfileResponse> createEmployeeProfile(
        @PathVariable UUID userId,
        @Valid @RequestBody CreateEmployeeProfileRequest request
    ) throws URISyntaxException {
        LOG.debug("REST request to create Employee profile for User ID: {}", userId);

        EmployeeProfileResponse result = employeeProfileService.createEmployeeProfile(userId, request);

        return ResponseEntity.created(new URI("/api/hr/employees/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /employees/{id}} : Updates an existing employee profile.
     *
     * @param id the ID of the employee to update
     * @param request the employee profile update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employee
     */
    @PutMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<EmployeeProfileResponse> updateEmployeeProfile(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeProfileRequest request
    ) {
        LOG.debug("REST request to update Employee profile: {}", id);

        EmployeeProfileResponse result = employeeProfileService.updateEmployeeProfile(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /employees} : Get all employees with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employees in body
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
    public ResponseEntity<Page<SimpleEmployeeProfileResponse>> getAllEmployees(Pageable pageable) {
        LOG.debug("REST request to get all Employees");

        Page<SimpleEmployeeProfileResponse> page = employeeProfileService.getAllEmployeeProfiles(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /employees/:id} : Get employee by ID.
     *
     * @param id the ID of the employee to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the employee
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
    public ResponseEntity<EmployeeProfileResponse> getEmployee(@PathVariable UUID id) {
        LOG.debug("REST request to get Employee: {}", id);

        EmployeeProfileResponse result = employeeProfileService.getEmployeeProfileById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /employees/search} : Search employees with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employees in body
     */
    @PostMapping("/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<SimpleEmployeeProfileResponse>> searchEmployees(
        @RequestBody EmployeeSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search Employees with criteria: {}", searchRequest);

        Page<SimpleEmployeeProfileResponse> page = employeeProfileService.searchEmployees(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /employees/:id} : Delete employee by ID.
     *
     * @param id the ID of the employee to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        LOG.debug("REST request to delete Employee: {}", id);

        employeeProfileService.deleteEmployeeProfile(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
