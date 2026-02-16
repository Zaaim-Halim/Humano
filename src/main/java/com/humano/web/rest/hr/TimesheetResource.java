package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateTimesheetRequest;
import com.humano.dto.hr.requests.TimesheetSearchRequest;
import com.humano.dto.hr.requests.UpdateTimesheetRequest;
import com.humano.dto.hr.responses.TimesheetResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.TimesheetService;
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
 * REST controller for managing timesheets.
 */
@RestController
@RequestMapping("/api/hr/timesheets")
public class TimesheetResource {

    private static final Logger LOG = LoggerFactory.getLogger(TimesheetResource.class);
    private static final String ENTITY_NAME = "timesheet";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TimesheetService timesheetService;

    public TimesheetResource(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    /**
     * {@code POST  /timesheets} : Create a new timesheet.
     *
     * @param request the timesheet creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new timesheet
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<TimesheetResponse> createTimesheet(@Valid @RequestBody CreateTimesheetRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Timesheet: {}", request);

        TimesheetResponse result = timesheetService.createTimesheet(request);

        return ResponseEntity.created(new URI("/api/hr/timesheets/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /timesheets/{id}} : Updates an existing timesheet.
     *
     * @param id the ID of the timesheet to update
     * @param request the timesheet update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated timesheet
     */
    @PutMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<TimesheetResponse> updateTimesheet(@PathVariable UUID id, @Valid @RequestBody UpdateTimesheetRequest request) {
        LOG.debug("REST request to update Timesheet: {}", id);

        TimesheetResponse result = timesheetService.updateTimesheet(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /timesheets} : Get all timesheets with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of timesheets in body
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
    public ResponseEntity<Page<TimesheetResponse>> getAllTimesheets(Pageable pageable) {
        LOG.debug("REST request to get all Timesheets");

        Page<TimesheetResponse> page = timesheetService.getAllTimesheets(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /timesheets/{id}} : Get timesheet by ID.
     *
     * @param id the ID of the timesheet to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the timesheet
     */
    @GetMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<TimesheetResponse> getTimesheet(@PathVariable UUID id) {
        LOG.debug("REST request to get Timesheet: {}", id);

        TimesheetResponse result = timesheetService.getTimesheetById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /timesheets/search} : Search timesheets with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of timesheets in body
     */
    @GetMapping("/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<TimesheetResponse>> searchTimesheets(TimesheetSearchRequest searchRequest, Pageable pageable) {
        LOG.debug("REST request to search Timesheets with criteria: {}", searchRequest);

        Page<TimesheetResponse> page = timesheetService.searchTimesheets(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /timesheets/employee/{employeeId}/search} : Search timesheets for a specific employee with criteria.
     *
     * @param employeeId the ID of the employee
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of timesheets in body
     */
    @GetMapping("/employee/{employeeId}/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Page<TimesheetResponse>> searchTimesheetsByEmployee(
        @PathVariable UUID employeeId,
        TimesheetSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search Timesheets for employee {} with criteria: {}", employeeId, searchRequest);

        Page<TimesheetResponse> page = timesheetService.searchTimesheetsByEmployee(employeeId, searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /timesheets/{id}} : Delete timesheet by ID.
     *
     * @param id the ID of the timesheet to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteTimesheet(@PathVariable UUID id) {
        LOG.debug("REST request to delete Timesheet: {}", id);

        timesheetService.deleteTimesheet(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
