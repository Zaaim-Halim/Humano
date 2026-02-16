package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.AttendanceEventSearchRequest;
import com.humano.dto.hr.requests.AttendanceSearchRequest;
import com.humano.dto.hr.requests.CreateAttendanceEventRequest;
import com.humano.dto.hr.requests.CreateAttendanceRequest;
import com.humano.dto.hr.requests.UpdateAttendanceRequest;
import com.humano.dto.hr.responses.AttendanceEventResponse;
import com.humano.dto.hr.responses.AttendanceResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.AttendanceService;
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
 * REST controller for managing attendance records.
 */
@RestController
@RequestMapping("/api/hr/attendances")
public class AttendanceResource {

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceResource.class);
    private static final String ENTITY_NAME = "attendance";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AttendanceService attendanceService;

    public AttendanceResource(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * {@code POST  /attendances} : Create a new attendance record.
     *
     * @param request the attendance creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new attendance
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
        "')"
    )
    public ResponseEntity<AttendanceResponse> createAttendance(@Valid @RequestBody CreateAttendanceRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create Attendance: {}", request);

        AttendanceResponse result = attendanceService.createAttendance(request);

        return ResponseEntity.created(new URI("/api/hr/attendances/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /attendances/{id}} : Updates an existing attendance record.
     *
     * @param id the ID of the attendance to update
     * @param request the attendance update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated attendance
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
    public ResponseEntity<AttendanceResponse> updateAttendance(@PathVariable UUID id, @Valid @RequestBody UpdateAttendanceRequest request) {
        LOG.debug("REST request to update Attendance: {}", id);

        AttendanceResponse result = attendanceService.updateAttendance(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /attendances} : Get all attendances with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of attendances in body
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
    public ResponseEntity<Page<AttendanceResponse>> getAllAttendances(Pageable pageable) {
        LOG.debug("REST request to get all Attendances");

        Page<AttendanceResponse> page = attendanceService.getAllAttendance(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /attendances/{id}} : Get attendance by ID.
     *
     * @param id the ID of the attendance to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the attendance
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
    public ResponseEntity<AttendanceResponse> getAttendance(@PathVariable UUID id) {
        LOG.debug("REST request to get Attendance: {}", id);

        AttendanceResponse result = attendanceService.getAttendanceById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /attendances/search} : Search attendances with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of attendances in body
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
    public ResponseEntity<Page<AttendanceResponse>> searchAttendances(AttendanceSearchRequest searchRequest, Pageable pageable) {
        LOG.debug("REST request to search Attendances with criteria: {}", searchRequest);

        Page<AttendanceResponse> page = attendanceService.searchAttendance(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /attendances/employee/{employeeId}/search} : Search attendances for a specific employee with criteria.
     *
     * @param employeeId the ID of the employee
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of attendances in body
     */
    @GetMapping("/employee/{employeeId}/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<AttendanceResponse>> searchAttendancesByEmployee(
        @PathVariable UUID employeeId,
        AttendanceSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search Attendances for employee {} with criteria: {}", employeeId, searchRequest);

        Page<AttendanceResponse> page = attendanceService.searchAttendanceByEmployee(employeeId, searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /attendances/{id}} : Delete attendance by ID.
     *
     * @param id the ID of the attendance to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteAttendance(@PathVariable UUID id) {
        LOG.debug("REST request to delete Attendance: {}", id);

        attendanceService.deleteAttendance(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    // Attendance Events

    /**
     * {@code POST  /attendances/events} : Create a new attendance event.
     *
     * @param request the attendance event creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the updated attendance
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/events")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<AttendanceResponse> createAttendanceEvent(@Valid @RequestBody CreateAttendanceEventRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create AttendanceEvent: {}", request);

        AttendanceResponse result = attendanceService.addAttendanceEvent(request);

        return ResponseEntity.created(new URI("/api/hr/attendances/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, "attendanceEvent", result.id().toString()))
            .body(result);
    }

    /**
     * {@code GET  /attendances/events/search} : Search attendance events with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of attendance events in body
     */
    @GetMapping("/events/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<AttendanceEventResponse>> searchAttendanceEvents(
        AttendanceEventSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search AttendanceEvents with criteria: {}", searchRequest);

        Page<AttendanceEventResponse> page = attendanceService.searchAttendanceEvents(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /attendances/employee/{employeeId}/events/search} : Search attendance events for a specific employee with criteria.
     *
     * @param employeeId the ID of the employee
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of attendance events in body
     */
    @GetMapping("/employee/{employeeId}/events/search")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<AttendanceEventResponse>> searchAttendanceEventsByEmployee(
        @PathVariable UUID employeeId,
        AttendanceEventSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search AttendanceEvents for employee {} with criteria: {}", employeeId, searchRequest);

        Page<AttendanceEventResponse> page = attendanceService.searchAttendanceEventsByEmployee(employeeId, searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }
}
