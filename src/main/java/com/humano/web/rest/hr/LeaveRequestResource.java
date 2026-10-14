package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateLeaveRequest;
import com.humano.dto.hr.requests.LeaveRequestSearchRequest;
import com.humano.dto.hr.requests.ProcessLeaveRequest;
import com.humano.dto.hr.responses.LeaveCalendarEntry;
import com.humano.dto.hr.responses.LeaveRequestResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.security.annotation.RequireHrStaffOrEmployee;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.LeaveRequestService;
import com.humano.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SecurityExpressions securityExpressions;

    public LeaveRequestResource(LeaveRequestService leaveRequestService, SecurityExpressions securityExpressions) {
        this.leaveRequestService = leaveRequestService;
        this.securityExpressions = securityExpressions;
    }

    /**
     * {@code POST  /leave-requests} : Create a new leave request.
     *
     * @param request the leave request creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new leave request
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @RequireHrStaffOrEmployee
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
    // Deciding needs approve rights (approvers) or manage rights (HR admins) — viewing is not enough.
    @PreAuthorize("@securityExpressions.hasAnyPermission('APPROVE_LEAVE', 'MANAGE_LEAVE_REQUESTS')")
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
    @RequirePermission(PermissionsConstants.VIEW_LEAVE_REQUESTS)
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
    @RequireHrStaffOrEmployee
    public ResponseEntity<LeaveRequestResponse> getLeaveRequest(@PathVariable UUID id) {
        LOG.debug("REST request to get LeaveRequest: {}", id);

        LeaveRequestResponse result = leaveRequestService.getLeaveRequestById(id);
        // Someone else's request is only visible to people who manage leave; others get a 404.
        if (
            !securityExpressions.isCurrentUser(result.employeeId()) &&
            !securityExpressions.hasAnyPermission("VIEW_LEAVE_REQUESTS", "MANAGE_LEAVE_REQUESTS", "APPROVE_LEAVE")
        ) {
            throw EntityNotFoundException.create("LeaveRequest", id);
        }

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
    @RequirePermission(PermissionsConstants.VIEW_LEAVE_REQUESTS)
    public ResponseEntity<Page<LeaveRequestResponse>> searchLeaveRequests(LeaveRequestSearchRequest searchRequest, Pageable pageable) {
        LOG.debug("REST request to search LeaveRequests with criteria: {}", searchRequest);

        Page<LeaveRequestResponse> page = leaveRequestService.searchLeaveRequests(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /leave-requests/calendar?from=&to=} : who is off when, for every signed-in
     * employee to plan around. No reasons or approvers; colleagues' leave types are hidden from
     * people who don't manage leave.
     */
    @GetMapping("/calendar")
    @RequireAuthenticated
    public ResponseEntity<List<LeaveCalendarEntry>> getCalendar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw new BadRequestAlertException("Choose a range of at most 400 days", "leaveRequest", "invalidrange");
        }
        boolean managesLeave = securityExpressions.hasAnyPermission("VIEW_LEAVE_REQUESTS", "MANAGE_LEAVE_REQUESTS", "APPROVE_LEAVE");
        return ResponseEntity.ok(leaveRequestService.getCalendar(from, to, id -> managesLeave || securityExpressions.isCurrentUser(id)));
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
    // One's own leave, or anyone's for people who manage leave (HR, managers, approvers).
    @PreAuthorize(
        "@securityExpressions.isCurrentUser(#employeeId) or " +
        "@securityExpressions.hasAnyPermission('VIEW_LEAVE_REQUESTS', 'MANAGE_LEAVE_REQUESTS', 'APPROVE_LEAVE')"
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
    @RequirePermission(PermissionsConstants.MANAGE_LEAVE_REQUESTS)
    public ResponseEntity<Void> deleteLeaveRequest(@PathVariable UUID id) {
        LOG.debug("REST request to delete LeaveRequest: {}", id);

        leaveRequestService.deleteLeaveRequest(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
