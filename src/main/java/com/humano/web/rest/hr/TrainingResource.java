package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateTrainingRequest;
import com.humano.dto.hr.requests.UpdateTrainingRequest;
import com.humano.dto.hr.responses.TrainingResponse;
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
 * REST controller for managing training programs.
 */
@RestController
@RequestMapping("/api/hr/trainings")
public class TrainingResource {

    private static final Logger LOG = LoggerFactory.getLogger(TrainingResource.class);
    private static final String ENTITY_NAME = "training";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TrainingService trainingService;

    public TrainingResource(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    /**
     * {@code POST  /trainings} : Create a new training program.
     *
     * @param request the training creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new training
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<TrainingResponse> createTraining(@Valid @RequestBody CreateTrainingRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Training: {}", request);

        TrainingResponse result = trainingService.createTraining(request);

        return ResponseEntity.created(new URI("/api/hr/trainings/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /trainings/{id}} : Updates an existing training program.
     *
     * @param id the ID of the training to update
     * @param request the training update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated training
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<TrainingResponse> updateTraining(@PathVariable UUID id, @Valid @RequestBody UpdateTrainingRequest request) {
        LOG.debug("REST request to update Training: {}", id);

        TrainingResponse result = trainingService.updateTraining(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /trainings} : Get all training programs with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of trainings in body
     */
    @GetMapping
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
    public ResponseEntity<Page<TrainingResponse>> getAllTrainings(Pageable pageable) {
        LOG.debug("REST request to get all Trainings");

        Page<TrainingResponse> page = trainingService.getAllTrainings(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /trainings/{id}} : Get training by ID.
     *
     * @param id the ID of the training to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the training
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
    public ResponseEntity<TrainingResponse> getTraining(@PathVariable UUID id) {
        LOG.debug("REST request to get Training: {}", id);

        TrainingResponse result = trainingService.getTrainingById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code DELETE  /trainings/{id}} : Delete training by ID.
     *
     * @param id the ID of the training to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteTraining(@PathVariable UUID id) {
        LOG.debug("REST request to delete Training: {}", id);

        trainingService.deleteTraining(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
