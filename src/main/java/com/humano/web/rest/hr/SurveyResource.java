package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateSurveyRequest;
import com.humano.dto.hr.requests.SubmitSurveyResponseRequest;
import com.humano.dto.hr.requests.UpdateSurveyRequest;
import com.humano.dto.hr.responses.SurveyResponse;
import com.humano.dto.hr.responses.SurveyResponseResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.SurveyService;
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
 * REST controller for managing surveys and survey responses.
 */
@RestController
@RequestMapping("/api/hr/surveys")
public class SurveyResource {

    private static final Logger LOG = LoggerFactory.getLogger(SurveyResource.class);
    private static final String ENTITY_NAME = "survey";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SurveyService surveyService;

    public SurveyResource(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /**
     * {@code POST  /surveys} : Create a new survey.
     *
     * @param request the survey creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new survey
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<SurveyResponse> createSurvey(@Valid @RequestBody CreateSurveyRequest request) throws URISyntaxException {
        LOG.debug("REST request to create Survey: {}", request);

        SurveyResponse result = surveyService.createSurvey(request);

        return ResponseEntity.created(new URI("/api/hr/surveys/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /surveys/{id}} : Updates an existing survey.
     *
     * @param id the ID of the survey to update
     * @param request the survey update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated survey
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<SurveyResponse> updateSurvey(@PathVariable UUID id, @Valid @RequestBody UpdateSurveyRequest request) {
        LOG.debug("REST request to update Survey: {}", id);

        SurveyResponse result = surveyService.updateSurvey(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /surveys} : Get all surveys with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of surveys in body
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
    public ResponseEntity<Page<SurveyResponse>> getAllSurveys(Pageable pageable) {
        LOG.debug("REST request to get all Surveys");

        Page<SurveyResponse> page = surveyService.getAllSurveys(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /surveys/{id}} : Get survey by ID.
     *
     * @param id the ID of the survey to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the survey
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
    public ResponseEntity<SurveyResponse> getSurvey(@PathVariable UUID id) {
        LOG.debug("REST request to get Survey: {}", id);

        SurveyResponse result = surveyService.getSurveyById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /surveys/active} : Get active surveys.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of active surveys in body
     */
    @GetMapping("/active")
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
    public ResponseEntity<Page<SurveyResponse>> getActiveSurveys(Pageable pageable) {
        LOG.debug("REST request to get active Surveys");

        Page<SurveyResponse> page = surveyService.getActiveSurveys(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /surveys/{id}} : Delete survey by ID.
     *
     * @param id the ID of the survey to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteSurvey(@PathVariable UUID id) {
        LOG.debug("REST request to delete Survey: {}", id);

        surveyService.deleteSurvey(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    // Survey Responses

    /**
     * {@code POST  /surveys/{surveyId}/responses} : Submit a survey response.
     *
     * @param surveyId the ID of the survey
     * @param request the survey response submission request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new survey response
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/{surveyId}/responses")
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
    public ResponseEntity<SurveyResponseResponse> submitSurveyResponse(
        @PathVariable UUID surveyId,
        @Valid @RequestBody SubmitSurveyResponseRequest request
    ) throws URISyntaxException {
        LOG.debug("REST request to submit Survey Response for survey {}: {}", surveyId, request);

        SurveyResponseResponse result = surveyService.submitSurveyResponse(request);

        return ResponseEntity.created(new URI("/api/hr/surveys/" + surveyId + "/responses/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, "surveyResponse", result.id().toString()))
            .body(result);
    }

    /**
     * {@code GET  /surveys/{surveyId}/responses} : Get survey responses for a survey.
     *
     * @param surveyId the ID of the survey
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of survey responses in body
     */
    @GetMapping("/{surveyId}/responses")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<SurveyResponseResponse>> getSurveyResponses(@PathVariable UUID surveyId, Pageable pageable) {
        LOG.debug("REST request to get Survey Responses for survey: {}", surveyId);

        Page<SurveyResponseResponse> page = surveyService.getSurveyResponsesBySurvey(surveyId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /surveys/responses/{responseId}} : Delete a survey response.
     *
     * @param responseId the ID of the survey response to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/responses/{responseId}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteSurveyResponse(@PathVariable UUID responseId) {
        LOG.debug("REST request to delete Survey Response: {}", responseId);

        surveyService.deleteSurveyResponse(responseId);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, "surveyResponse", responseId.toString()))
            .build();
    }
}
