package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateLeaveRequest;
import com.humano.dto.hr.requests.LeaveRequestSearchRequest;
import com.humano.dto.hr.requests.ProcessLeaveRequest;
import com.humano.dto.hr.responses.LeaveRequestResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.LeaveRequestService;
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
 * REST controller for managing leave requests.
 */
@RestController
@RequestMapping("/api/hr/leave-requests")
public class LeaveRequestResource {

    private static final Logger LOG = LoggerFactory.getLogger(LeaveRequestResource.class);
    private static final String ENTITY_NAME = "leaveRequest";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestResource(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    /**
     * {@code POST  /leave-requests} : Create a new leave request.
     *
     * @param request the leave request creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new leave request
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
    public ResponseEntity<LeaveRequestResponse> createLeaveRequest(@Valid @RequestBody CreateLeaveRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create LeaveRequest: {}", request);

        LeaveRequestResponse result = leaveRequestService.createLeaveRequest(request);

        return ResponseEntity.created(new URI("/api/hr/leave-requests/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /leave-requests/{id}/process} : Process an existing leave request (approve/reject).
     *
     * @param id the ID of the leave request to process
     * @param request the leave request process request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated leave request
     */
    @PutMapping("/{id}/process")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<LeaveRequestResponse> processLeaveRequest(
        @PathVariable UUID id,
        @Valid @RequestBody ProcessLeaveRequest request
    ) {
        LOG.debug("REST request to process LeaveRequest: {}", id);

        LeaveRequestResponse result = leaveRequestService.processLeaveRequest(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /leave-requests} : Get all leave requests with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of leave requests in body
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
    public ResponseEntity<Page<LeaveRequestResponse>> getAllLeaveRequests(Pageable pageable) {
        LOG.debug("REST request to get all LeaveRequests");

        Page<LeaveRequestResponse> page = leaveRequestService.getAllLeaveRequests(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /leave-requests/{id}} : Get leave request by ID.
     *
     * @param id the ID of the leave request to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the leave request
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
    public ResponseEntity<LeaveRequestResponse> getLeaveRequest(@PathVariable UUID id) {
        LOG.debug("REST request to get LeaveRequest: {}", id);

        LeaveRequestResponse result = leaveRequestService.getLeaveRequestById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /leave-requests/search} : Search leave requests with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of leave requests in body
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
    public ResponseEntity<Page<LeaveRequestResponse>> searchLeaveRequests(LeaveRequestSearchRequest searchRequest, Pageable pageable) {
        LOG.debug("REST request to search LeaveRequests with criteria: {}", searchRequest);

        Page<LeaveRequestResponse> page = leaveRequestService.searchLeaveRequests(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /leave-requests/employee/{employeeId}/search} : Search leave requests for a specific employee with criteria.
     *
     * @param employeeId the ID of the employee
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of leave requests in body
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
    public ResponseEntity<Page<LeaveRequestResponse>> searchLeaveRequestsByEmployee(
        @PathVariable UUID employeeId,
        LeaveRequestSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search LeaveRequests for employee {} with criteria: {}", employeeId, searchRequest);

        Page<LeaveRequestResponse> page = leaveRequestService.searchLeaveRequestsByEmployee(employeeId, searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /leave-requests/{id}} : Delete leave request by ID.
     *
     * @param id the ID of the leave request to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteLeaveRequest(@PathVariable UUID id) {
        LOG.debug("REST request to delete LeaveRequest: {}", id);

        leaveRequestService.deleteLeaveRequest(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
