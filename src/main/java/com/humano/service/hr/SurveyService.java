package com.humano.service.hr;

import com.humano.domain.hr.Employee;
import com.humano.domain.hr.Survey;
import com.humano.domain.hr.SurveyResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.SurveyRepository;
import com.humano.repository.hr.SurveyResponseRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreateSurveyRequest;
import com.humano.service.hr.dto.requests.SubmitSurveyResponseRequest;
import com.humano.service.hr.dto.requests.UpdateSurveyRequest;
import com.humano.service.hr.dto.responses.SurveyResponseResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing surveys and survey responses.
 * Handles CRUD operations for survey entities and response submissions.
 */
@Service
public class SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final EmployeeRepository employeeRepository;

    public SurveyService(
        SurveyRepository surveyRepository,
        SurveyResponseRepository surveyResponseRepository,
        EmployeeRepository employeeRepository
    ) {
        this.surveyRepository = surveyRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new survey.
     *
     * @param request the survey creation request
     * @return the created survey response DTO
     */
    @Transactional
    public com.humano.service.hr.dto.responses.SurveyResponse createSurvey(CreateSurveyRequest request) {
        log.debug("Request to create Survey: {}", request);

        Survey survey = new Survey();
        survey.setTitle(request.title());
        survey.setDescription(request.description());
        survey.setStartDate(request.startDate());
        survey.setEndDate(request.endDate());

        Survey savedSurvey = surveyRepository.save(survey);
        log.info("Created survey with ID: {}", savedSurvey.getId());

        return mapToSurveyResponse(savedSurvey);
    }

    /**
     * Update an existing survey.
     *
     * @param id the ID of the survey to update
     * @param request the survey update request
     * @return the updated survey response DTO
     */
    @Transactional
    public com.humano.service.hr.dto.responses.SurveyResponse updateSurvey(UUID id, UpdateSurveyRequest request) {
        log.debug("Request to update Survey: {}", id);

        return surveyRepository
            .findById(id)
            .map(survey -> {
                if (request.title() != null) {
                    survey.setTitle(request.title());
                }
                if (request.description() != null) {
                    survey.setDescription(request.description());
                }
                if (request.startDate() != null) {
                    survey.setStartDate(request.startDate());
                }
                if (request.endDate() != null) {
                    survey.setEndDate(request.endDate());
                }
                return mapToSurveyResponse(surveyRepository.save(survey));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Survey", id));
    }

    /**
     * Get a survey by ID.
     *
     * @param id the ID of the survey
     * @return the survey response DTO
     */
    @Transactional(readOnly = true)
    public com.humano.service.hr.dto.responses.SurveyResponse getSurveyById(UUID id) {
        log.debug("Request to get Survey by ID: {}", id);

        return surveyRepository.findById(id).map(this::mapToSurveyResponse).orElseThrow(() -> EntityNotFoundException.create("Survey", id));
    }

    /**
     * Get all surveys with pagination.
     *
     * @param pageable pagination information
     * @return page of survey response DTOs
     */
    @Transactional(readOnly = true)
    public Page<com.humano.service.hr.dto.responses.SurveyResponse> getAllSurveys(Pageable pageable) {
        log.debug("Request to get all Surveys");

        return surveyRepository.findAll(pageable).map(this::mapToSurveyResponse);
    }

    /**
     * Get active surveys (currently within date range).
     *
     * @param pageable pagination information
     * @return page of survey response DTOs
     */
    @Transactional(readOnly = true)
    public Page<com.humano.service.hr.dto.responses.SurveyResponse> getActiveSurveys(Pageable pageable) {
        log.debug("Request to get active Surveys");
        LocalDate today = LocalDate.now();

        return surveyRepository
            .findByStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today, pageable)
            .map(this::mapToSurveyResponse);
    }

    /**
     * Delete a survey by ID.
     *
     * @param id the ID of the survey to delete
     */
    @Transactional
    public void deleteSurvey(UUID id) {
        log.debug("Request to delete Survey: {}", id);

        if (!surveyRepository.existsById(id)) {
            throw EntityNotFoundException.create("Survey", id);
        }
        surveyRepository.deleteById(id);
        log.info("Deleted survey with ID: {}", id);
    }

    /**
     * Submit a response to a survey.
     *
     * @param request the survey response submission request
     * @return the survey response response DTO
     */
    @Transactional
    public SurveyResponseResponse submitSurveyResponse(SubmitSurveyResponseRequest request) {
        log.debug("Request to submit SurveyResponse: {}", request);

        Survey survey = surveyRepository
            .findById(request.surveyId())
            .orElseThrow(() -> EntityNotFoundException.create("Survey", request.surveyId()));

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        SurveyResponse response = new SurveyResponse();
        response.setSurvey(survey);
        response.setEmployee(employee);
        response.setResponse(request.response());
        response.setSubmittedAt(LocalDateTime.now());

        SurveyResponse savedResponse = surveyResponseRepository.save(response);
        log.info("Submitted survey response with ID: {}", savedResponse.getId());

        return mapToSurveyResponseResponse(savedResponse);
    }

    /**
     * Get survey responses by survey.
     *
     * @param surveyId the survey ID
     * @param pageable pagination information
     * @return page of survey response response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SurveyResponseResponse> getSurveyResponsesBySurvey(UUID surveyId, Pageable pageable) {
        log.debug("Request to get SurveyResponses by Survey: {}", surveyId);

        return surveyResponseRepository.findBySurveyId(surveyId, pageable).map(this::mapToSurveyResponseResponse);
    }

    /**
     * Delete a survey response by ID.
     *
     * @param id the ID of the survey response to delete
     */
    @Transactional
    public void deleteSurveyResponse(UUID id) {
        log.debug("Request to delete SurveyResponse: {}", id);

        if (!surveyResponseRepository.existsById(id)) {
            throw EntityNotFoundException.create("SurveyResponse", id);
        }
        surveyResponseRepository.deleteById(id);
        log.info("Deleted survey response with ID: {}", id);
    }

    private com.humano.service.hr.dto.responses.SurveyResponse mapToSurveyResponse(Survey survey) {
        return new com.humano.service.hr.dto.responses.SurveyResponse(
            survey.getId(),
            survey.getTitle(),
            survey.getDescription(),
            survey.getStartDate(),
            survey.getEndDate(),
            survey.getResponses() != null ? survey.getResponses().size() : 0,
            survey.getCreatedBy(),
            survey.getCreatedDate(),
            survey.getLastModifiedBy(),
            survey.getLastModifiedDate()
        );
    }

    private SurveyResponseResponse mapToSurveyResponseResponse(SurveyResponse response) {
        return new SurveyResponseResponse(
            response.getId(),
            response.getSurvey().getId(),
            response.getSurvey().getTitle(),
            response.getResponse(),
            response.getSubmittedAt(),
            response.getCreatedBy(),
            response.getCreatedDate(),
            response.getLastModifiedBy(),
            response.getLastModifiedDate()
        );
    }
}
