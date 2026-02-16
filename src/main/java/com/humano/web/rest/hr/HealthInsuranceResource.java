package com.humano.web.rest.hr;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import com.humano.dto.hr.requests.CreateHealthInsuranceRequest;
import com.humano.dto.hr.requests.UpdateHealthInsuranceRequest;
import com.humano.dto.hr.responses.HealthInsuranceResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.HealthInsuranceService;
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
 * REST controller for managing health insurance records.
 */
@RestController
@RequestMapping("/api/hr/health-insurance")
public class HealthInsuranceResource {

    private static final Logger LOG = LoggerFactory.getLogger(HealthInsuranceResource.class);
    private static final String ENTITY_NAME = "healthInsurance";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final HealthInsuranceService healthInsuranceService;

    public HealthInsuranceResource(HealthInsuranceService healthInsuranceService) {
        this.healthInsuranceService = healthInsuranceService;
    }

    /**
     * {@code POST  /health-insurance} : Create a new health insurance record.
     *
     * @param request the health insurance creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new health insurance
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
    public ResponseEntity<HealthInsuranceResponse> createHealthInsurance(@Valid @RequestBody CreateHealthInsuranceRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create HealthInsurance: {}", request);

        HealthInsuranceResponse result = healthInsuranceService.createHealthInsurance(request);

        return ResponseEntity.created(new URI("/api/hr/health-insurance/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /health-insurance/{id}} : Updates an existing health insurance record.
     *
     * @param id the ID of the health insurance to update
     * @param request the health insurance update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated health insurance
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
    public ResponseEntity<HealthInsuranceResponse> updateHealthInsurance(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateHealthInsuranceRequest request
    ) {
        LOG.debug("REST request to update HealthInsurance: {}", id);

        HealthInsuranceResponse result = healthInsuranceService.updateHealthInsurance(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /health-insurance} : Get all health insurance records with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of health insurance records in body
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
    public ResponseEntity<Page<HealthInsuranceResponse>> getAllHealthInsurance(Pageable pageable) {
        LOG.debug("REST request to get all HealthInsurance records");

        Page<HealthInsuranceResponse> page = healthInsuranceService.getAllHealthInsurance(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /health-insurance/{id}} : Get health insurance by ID.
     *
     * @param id the ID of the health insurance to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the health insurance
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
    public ResponseEntity<HealthInsuranceResponse> getHealthInsurance(@PathVariable UUID id) {
        LOG.debug("REST request to get HealthInsurance: {}", id);

        HealthInsuranceResponse result = healthInsuranceService.getHealthInsuranceById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /health-insurance/employee/{employeeId}} : Get health insurance records by employee.
     *
     * @param employeeId the ID of the employee
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of health insurance records in body
     */
    @GetMapping("/employee/{employeeId}")
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
    public ResponseEntity<Page<HealthInsuranceResponse>> getHealthInsuranceByEmployee(@PathVariable UUID employeeId, Pageable pageable) {
        LOG.debug("REST request to get HealthInsurance by employee: {}", employeeId);

        Page<HealthInsuranceResponse> page = healthInsuranceService.getHealthInsuranceByEmployee(employeeId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /health-insurance/status/{status}} : Get health insurance records by status.
     *
     * @param status the status to filter by
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of health insurance records in body
     */
    @GetMapping("/status/{status}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<HealthInsuranceResponse>> getHealthInsuranceByStatus(
        @PathVariable HealthInsuranceStatus status,
        Pageable pageable
    ) {
        LOG.debug("REST request to get HealthInsurance by status: {}", status);

        Page<HealthInsuranceResponse> page = healthInsuranceService.getHealthInsuranceByStatus(status, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /health-insurance/active} : Get active health insurance records.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of active health insurance records in body
     */
    @GetMapping("/active")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<HealthInsuranceResponse>> getActiveHealthInsurance(Pageable pageable) {
        LOG.debug("REST request to get active HealthInsurance records");

        Page<HealthInsuranceResponse> page = healthInsuranceService.getActiveHealthInsurance(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /health-insurance/{id}} : Delete health insurance by ID.
     *
     * @param id the ID of the health insurance to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteHealthInsurance(@PathVariable UUID id) {
        LOG.debug("REST request to delete HealthInsurance: {}", id);

        healthInsuranceService.deleteHealthInsurance(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
