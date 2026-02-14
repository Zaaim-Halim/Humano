package com.humano.service.hr;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.HealthInsurance;
import com.humano.dto.hr.requests.CreateHealthInsuranceRequest;
import com.humano.dto.hr.requests.UpdateHealthInsuranceRequest;
import com.humano.dto.hr.responses.HealthInsuranceResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.HealthInsuranceRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing health insurance records.
 * Handles CRUD operations for health insurance entities.
 */
@Service
public class HealthInsuranceService {

    private static final Logger log = LoggerFactory.getLogger(HealthInsuranceService.class);
    private static final String ENTITY_NAME = "healthInsurance";

    private final HealthInsuranceRepository healthInsuranceRepository;
    private final EmployeeRepository employeeRepository;

    public HealthInsuranceService(HealthInsuranceRepository healthInsuranceRepository, EmployeeRepository employeeRepository) {
        this.healthInsuranceRepository = healthInsuranceRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new health insurance record.
     *
     * @param request the health insurance creation request
     * @return the created health insurance response
     */
    @Transactional
    public HealthInsuranceResponse createHealthInsurance(CreateHealthInsuranceRequest request) {
        log.debug("Request to create HealthInsurance: {}", request);

        // Validate dates
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestAlertException("End date cannot be before start date", ENTITY_NAME, "invaliddates");
        }

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        HealthInsurance insurance = new HealthInsurance();
        insurance.setEmployee(employee);
        insurance.setProviderName(request.providerName());
        insurance.setPolicyNumber(request.policyNumber());
        insurance.setStartDate(request.startDate());
        insurance.setEndDate(request.endDate());
        insurance.setCoverageAmount(request.coverageAmount());
        insurance.setStatus(request.status());

        HealthInsurance savedInsurance = healthInsuranceRepository.save(insurance);
        log.info("Created health insurance with ID: {}", savedInsurance.getId());

        return mapToResponse(savedInsurance);
    }

    /**
     * Update an existing health insurance record.
     *
     * @param id the ID of the health insurance to update
     * @param request the health insurance update request
     * @return the updated health insurance response
     */
    @Transactional
    public HealthInsuranceResponse updateHealthInsurance(UUID id, UpdateHealthInsuranceRequest request) {
        log.debug("Request to update HealthInsurance: {}", id);

        return healthInsuranceRepository
            .findById(id)
            .map(insurance -> {
                if (request.providerName() != null) {
                    insurance.setProviderName(request.providerName());
                }
                if (request.policyNumber() != null) {
                    insurance.setPolicyNumber(request.policyNumber());
                }
                if (request.startDate() != null) {
                    insurance.setStartDate(request.startDate());
                }
                if (request.endDate() != null) {
                    insurance.setEndDate(request.endDate());
                }
                if (request.coverageAmount() != null) {
                    insurance.setCoverageAmount(request.coverageAmount());
                }
                if (request.status() != null) {
                    insurance.setStatus(request.status());
                }
                return mapToResponse(healthInsuranceRepository.save(insurance));
            })
            .orElseThrow(() -> EntityNotFoundException.create("HealthInsurance", id));
    }

    /**
     * Get a health insurance by ID.
     *
     * @param id the ID of the health insurance
     * @return the health insurance response
     */
    @Transactional(readOnly = true)
    public HealthInsuranceResponse getHealthInsuranceById(UUID id) {
        log.debug("Request to get HealthInsurance by ID: {}", id);

        return healthInsuranceRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("HealthInsurance", id));
    }

    /**
     * Get all health insurance records with pagination.
     *
     * @param pageable pagination information
     * @return page of health insurance responses
     */
    @Transactional(readOnly = true)
    public Page<HealthInsuranceResponse> getAllHealthInsurance(Pageable pageable) {
        log.debug("Request to get all HealthInsurance records");

        return healthInsuranceRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get health insurance records by employee.
     *
     * @param employeeId the employee ID
     * @param pageable pagination information
     * @return page of health insurance responses
     */
    @Transactional(readOnly = true)
    public Page<HealthInsuranceResponse> getHealthInsuranceByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get HealthInsurance by Employee: {}", employeeId);

        return healthInsuranceRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    /**
     * Get health insurance records by status.
     *
     * @param status the health insurance status
     * @param pageable pagination information
     * @return page of health insurance responses
     */
    @Transactional(readOnly = true)
    public Page<HealthInsuranceResponse> getHealthInsuranceByStatus(HealthInsuranceStatus status, Pageable pageable) {
        log.debug("Request to get HealthInsurance by Status: {}", status);

        return healthInsuranceRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    /**
     * Get active health insurance records.
     *
     * @param pageable pagination information
     * @return page of health insurance responses
     */
    @Transactional(readOnly = true)
    public Page<HealthInsuranceResponse> getActiveHealthInsurance(Pageable pageable) {
        return getHealthInsuranceByStatus(HealthInsuranceStatus.ACTIVE, pageable);
    }

    /**
     * Delete a health insurance record by ID.
     *
     * @param id the ID of the health insurance to delete
     */
    @Transactional
    public void deleteHealthInsurance(UUID id) {
        log.debug("Request to delete HealthInsurance: {}", id);

        if (!healthInsuranceRepository.existsById(id)) {
            throw EntityNotFoundException.create("HealthInsurance", id);
        }
        healthInsuranceRepository.deleteById(id);
        log.info("Deleted health insurance with ID: {}", id);
    }

    private HealthInsuranceResponse mapToResponse(HealthInsurance insurance) {
        String employeeName = insurance.getEmployee().getFirstName() + " " + insurance.getEmployee().getLastName();

        return new HealthInsuranceResponse(
            insurance.getId(),
            insurance.getEmployee().getId(),
            employeeName,
            insurance.getProviderName(),
            insurance.getPolicyNumber(),
            insurance.getStartDate(),
            insurance.getEndDate(),
            insurance.getCoverageAmount(),
            insurance.getStatus(),
            insurance.getCreatedBy(),
            insurance.getCreatedDate(),
            insurance.getLastModifiedBy(),
            insurance.getLastModifiedDate()
        );
    }
}
