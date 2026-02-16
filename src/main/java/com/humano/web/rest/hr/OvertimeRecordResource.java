package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateOvertimeRecordRequest;
import com.humano.dto.hr.requests.OvertimeRecordSearchRequest;
import com.humano.dto.hr.requests.ProcessOvertimeRecordRequest;
import com.humano.dto.hr.responses.OvertimeRecordResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.OvertimeRecordService;
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
 * REST controller for managing overtime records.
 */
@RestController
@RequestMapping("/api/hr/overtime-records")
public class OvertimeRecordResource {

    private static final Logger LOG = LoggerFactory.getLogger(OvertimeRecordResource.class);
    private static final String ENTITY_NAME = "overtimeRecord";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final OvertimeRecordService overtimeRecordService;

    public OvertimeRecordResource(OvertimeRecordService overtimeRecordService) {
        this.overtimeRecordService = overtimeRecordService;
    }

    /**
     * {@code POST  /overtime-records} : Create a new overtime record.
     *
     * @param request the overtime record creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new overtime record
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
    public ResponseEntity<OvertimeRecordResponse> createOvertimeRecord(@Valid @RequestBody CreateOvertimeRecordRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create OvertimeRecord: {}", request);

        OvertimeRecordResponse result = overtimeRecordService.createOvertimeRecord(request);

        return ResponseEntity.created(new URI("/api/hr/overtime-records/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /overtime-records/{id}/process} : Process an existing overtime record (approve/reject).
     *
     * @param id the ID of the overtime record to process
     * @param request the overtime record process request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated overtime record
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
    public ResponseEntity<OvertimeRecordResponse> processOvertimeRecord(
        @PathVariable UUID id,
        @Valid @RequestBody ProcessOvertimeRecordRequest request
    ) {
        LOG.debug("REST request to process OvertimeRecord: {}", id);

        OvertimeRecordResponse result = overtimeRecordService.processOvertimeRecord(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /overtime-records} : Get all overtime records with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of overtime records in body
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
    public ResponseEntity<Page<OvertimeRecordResponse>> getAllOvertimeRecords(Pageable pageable) {
        LOG.debug("REST request to get all OvertimeRecords");

        Page<OvertimeRecordResponse> page = overtimeRecordService.getAllOvertimeRecords(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /overtime-records/{id}} : Get overtime record by ID.
     *
     * @param id the ID of the overtime record to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the overtime record
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
    public ResponseEntity<OvertimeRecordResponse> getOvertimeRecord(@PathVariable UUID id) {
        LOG.debug("REST request to get OvertimeRecord: {}", id);

        OvertimeRecordResponse result = overtimeRecordService.getOvertimeRecordById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /overtime-records/search} : Search overtime records with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of overtime records in body
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
    public ResponseEntity<Page<OvertimeRecordResponse>> searchOvertimeRecords(
        OvertimeRecordSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search OvertimeRecords with criteria: {}", searchRequest);

        Page<OvertimeRecordResponse> page = overtimeRecordService.searchOvertimeRecords(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /overtime-records/employee/{employeeId}/search} : Search overtime records for a specific employee with criteria.
     *
     * @param employeeId the ID of the employee
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of overtime records in body
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
    public ResponseEntity<Page<OvertimeRecordResponse>> searchOvertimeRecordsByEmployee(
        @PathVariable UUID employeeId,
        OvertimeRecordSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search OvertimeRecords for employee {} with criteria: {}", employeeId, searchRequest);

        Page<OvertimeRecordResponse> page = overtimeRecordService.searchOvertimeRecordsByEmployee(employeeId, searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /overtime-records/{id}} : Delete overtime record by ID.
     *
     * @param id the ID of the overtime record to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteOvertimeRecord(@PathVariable UUID id) {
        LOG.debug("REST request to delete OvertimeRecord: {}", id);

        overtimeRecordService.deleteOvertimeRecord(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
