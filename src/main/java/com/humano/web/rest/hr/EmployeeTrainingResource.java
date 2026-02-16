package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.EmployeeTrainingSearchRequest;
import com.humano.dto.hr.requests.EnrollEmployeeTrainingRequest;
import com.humano.dto.hr.requests.UpdateEmployeeTrainingRequest;
import com.humano.dto.hr.responses.EmployeeTrainingResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.TrainingService;
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
 * REST controller for managing employee trainings.
 */
@RestController
@RequestMapping("/api/hr/employee-trainings")
public class EmployeeTrainingResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeTrainingResource.class);
    private static final String ENTITY_NAME = "employeeTraining";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TrainingService trainingService;

    public EmployeeTrainingResource(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    /**
     * {@code POST  /employee-trainings} : Create a new employee training.
     *
     * @param request the employee training creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new employee training
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
    public ResponseEntity<EmployeeTrainingResponse> createEmployeeTraining(@Valid @RequestBody EnrollEmployeeTrainingRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to create EmployeeTraining: {}", request);

        EmployeeTrainingResponse result = trainingService.enrollEmployee(request);

        return ResponseEntity.created(new URI("/api/hr/employee-trainings/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /employee-trainings/{id}} : Updates an existing employee training.
     *
     * @param id the ID of the employee training to update
     * @param request the employee training update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employee training
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
    public ResponseEntity<EmployeeTrainingResponse> updateEmployeeTraining(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeTrainingRequest request
    ) {
        LOG.debug("REST request to update EmployeeTraining: {}", id);

        EmployeeTrainingResponse result = trainingService.updateEmployeeTraining(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /employee-trainings} : Get all employee trainings with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employee trainings in body
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
    public ResponseEntity<Page<EmployeeTrainingResponse>> getAllEmployeeTrainings(Pageable pageable) {
        LOG.debug("REST request to get all EmployeeTrainings");

        Page<EmployeeTrainingResponse> page = trainingService.getEmployeeTrainingsByTraining(null, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /employee-trainings/{id}} : Get employee training by ID.
     *
     * @param id the ID of the employee training to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the employee training
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
    public ResponseEntity<EmployeeTrainingResponse> getEmployeeTraining(@PathVariable UUID id) {
        LOG.debug("REST request to get EmployeeTraining: {}", id);

        EmployeeTrainingResponse result = trainingService.getEmployeeTrainingById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /employee-trainings/search} : Search employee trainings with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employee trainings in body
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
    public ResponseEntity<Page<EmployeeTrainingResponse>> searchEmployeeTrainings(
        EmployeeTrainingSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search EmployeeTrainings with criteria: {}", searchRequest);

        Page<EmployeeTrainingResponse> page = trainingService.searchEmployeeTrainings(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /employee-trainings/{id}} : Delete employee training by ID.
     *
     * @param id the ID of the employee training to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteEmployeeTraining(@PathVariable UUID id) {
        LOG.debug("REST request to delete EmployeeTraining: {}", id);

        trainingService.removeEmployeeFromTraining(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
