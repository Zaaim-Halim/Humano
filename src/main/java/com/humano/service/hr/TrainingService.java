package com.humano.service.hr;

import com.humano.domain.enumeration.hr.TrainingStatus;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.EmployeeTraining;
import com.humano.domain.hr.Training;
import com.humano.dto.hr.requests.CreateTrainingRequest;
import com.humano.dto.hr.requests.EnrollEmployeeTrainingRequest;
import com.humano.dto.hr.requests.UpdateEmployeeTrainingRequest;
import com.humano.dto.hr.requests.UpdateTrainingRequest;
import com.humano.dto.hr.responses.EmployeeTrainingResponse;
import com.humano.dto.hr.responses.TrainingResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.EmployeeTrainingRepository;
import com.humano.repository.hr.TrainingRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing training programs and employee training records.
 * Handles CRUD operations for training entities and employee enrollments.
 */
@Service
public class TrainingService {

    private static final Logger log = LoggerFactory.getLogger(TrainingService.class);

    private final TrainingRepository trainingRepository;
    private final EmployeeTrainingRepository employeeTrainingRepository;
    private final EmployeeRepository employeeRepository;

    public TrainingService(
        TrainingRepository trainingRepository,
        EmployeeTrainingRepository employeeTrainingRepository,
        EmployeeRepository employeeRepository
    ) {
        this.trainingRepository = trainingRepository;
        this.employeeTrainingRepository = employeeTrainingRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new training program.
     *
     * @param request the training creation request
     * @return the created training response
     */
    @Transactional
    public TrainingResponse createTraining(CreateTrainingRequest request) {
        log.debug("Request to create Training: {}", request);

        Training training = new Training();
        training.setName(request.name());
        training.setProvider(request.provider());
        training.setStartDate(request.startDate());
        training.setEndDate(request.endDate());
        training.setDescription(request.description());
        training.setLocation(request.location());
        training.setCertificate(request.certificate());

        Training savedTraining = trainingRepository.save(training);
        log.info("Created training with ID: {}", savedTraining.getId());

        return mapToTrainingResponse(savedTraining);
    }

    /**
     * Update an existing training program.
     *
     * @param id the ID of the training to update
     * @param request the training update request
     * @return the updated training response
     */
    @Transactional
    public TrainingResponse updateTraining(UUID id, UpdateTrainingRequest request) {
        log.debug("Request to update Training: {}", id);

        return trainingRepository
            .findById(id)
            .map(training -> {
                if (request.name() != null) {
                    training.setName(request.name());
                }
                if (request.provider() != null) {
                    training.setProvider(request.provider());
                }
                if (request.startDate() != null) {
                    training.setStartDate(request.startDate());
                }
                if (request.endDate() != null) {
                    training.setEndDate(request.endDate());
                }
                if (request.description() != null) {
                    training.setDescription(request.description());
                }
                if (request.location() != null) {
                    training.setLocation(request.location());
                }
                if (request.certificate() != null) {
                    training.setCertificate(request.certificate());
                }
                return mapToTrainingResponse(trainingRepository.save(training));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Training", id));
    }

    /**
     * Get a training by ID.
     *
     * @param id the ID of the training
     * @return the training response
     */
    @Transactional(readOnly = true)
    public TrainingResponse getTrainingById(UUID id) {
        log.debug("Request to get Training by ID: {}", id);

        return trainingRepository
            .findById(id)
            .map(this::mapToTrainingResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Training", id));
    }

    /**
     * Get all trainings with pagination.
     *
     * @param pageable pagination information
     * @return page of training responses
     */
    @Transactional(readOnly = true)
    public Page<TrainingResponse> getAllTrainings(Pageable pageable) {
        log.debug("Request to get all Trainings");

        return trainingRepository.findAll(pageable).map(this::mapToTrainingResponse);
    }

    /**
     * Delete a training by ID.
     *
     * @param id the ID of the training to delete
     */
    @Transactional
    public void deleteTraining(UUID id) {
        log.debug("Request to delete Training: {}", id);

        if (!trainingRepository.existsById(id)) {
            throw EntityNotFoundException.create("Training", id);
        }
        trainingRepository.deleteById(id);
        log.info("Deleted training with ID: {}", id);
    }

    /**
     * Enroll an employee in a training program.
     *
     * @param request the employee training enrollment request
     * @return the employee training response
     */
    @Transactional
    public EmployeeTrainingResponse enrollEmployee(EnrollEmployeeTrainingRequest request) {
        log.debug("Request to enroll Employee in Training: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        Training training = trainingRepository
            .findById(request.trainingId())
            .orElseThrow(() -> EntityNotFoundException.create("Training", request.trainingId()));

        EmployeeTraining employeeTraining = new EmployeeTraining();
        employeeTraining.setEmployee(employee);
        employeeTraining.setTraining(training);
        employeeTraining.setStatus(request.status() != null ? request.status() : TrainingStatus.PLANNED);
        employeeTraining.setDescription(request.description());

        EmployeeTraining savedRecord = employeeTrainingRepository.save(employeeTraining);
        log.info("Enrolled employee {} in training {} with ID: {}", request.employeeId(), request.trainingId(), savedRecord.getId());

        return mapToEmployeeTrainingResponse(savedRecord);
    }

    /**
     * Update an employee's training record.
     *
     * @param id the ID of the employee training record
     * @param request the update request
     * @return the updated employee training response
     */
    @Transactional
    public EmployeeTrainingResponse updateEmployeeTraining(UUID id, UpdateEmployeeTrainingRequest request) {
        log.debug("Request to update EmployeeTraining: {}", id);

        return employeeTrainingRepository
            .findById(id)
            .map(record -> {
                if (request.status() != null) {
                    record.setStatus(request.status());
                }
                if (request.description() != null) {
                    record.setDescription(request.description());
                }
                if (request.completionDate() != null) {
                    record.setCompletionDate(request.completionDate());
                }
                if (request.feedback() != null) {
                    record.setFeedback(request.feedback());
                }
                return mapToEmployeeTrainingResponse(employeeTrainingRepository.save(record));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeTraining", id));
    }

    /**
     * Get employee training records by employee.
     *
     * @param employeeId the employee ID
     * @param pageable pagination information
     * @return page of employee training responses
     */
    @Transactional(readOnly = true)
    public Page<EmployeeTrainingResponse> getEmployeeTrainingsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get EmployeeTrainings by Employee: {}", employeeId);

        return employeeTrainingRepository.findByEmployeeId(employeeId, pageable).map(this::mapToEmployeeTrainingResponse);
    }

    /**
     * Get employee training records by training.
     *
     * @param trainingId the training ID
     * @param pageable pagination information
     * @return page of employee training responses
     */
    @Transactional(readOnly = true)
    public Page<EmployeeTrainingResponse> getEmployeeTrainingsByTraining(UUID trainingId, Pageable pageable) {
        log.debug("Request to get EmployeeTrainings by Training: {}", trainingId);

        return employeeTrainingRepository.findByTrainingId(trainingId, pageable).map(this::mapToEmployeeTrainingResponse);
    }

    /**
     * Remove an employee from a training.
     *
     * @param id the ID of the employee training record to delete
     */
    @Transactional
    public void removeEmployeeFromTraining(UUID id) {
        log.debug("Request to remove EmployeeTraining: {}", id);

        if (!employeeTrainingRepository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeTraining", id);
        }
        employeeTrainingRepository.deleteById(id);
        log.info("Removed employee training with ID: {}", id);
    }

    private TrainingResponse mapToTrainingResponse(Training training) {
        return new TrainingResponse(
            training.getId(),
            training.getName(),
            training.getProvider(),
            training.getStartDate(),
            training.getEndDate(),
            training.getDescription(),
            training.getLocation(),
            training.getCertificate(),
            training.getEmployeeTrainings() != null ? training.getEmployeeTrainings().size() : 0,
            training.getCreatedBy(),
            training.getCreatedDate(),
            training.getLastModifiedBy(),
            training.getLastModifiedDate()
        );
    }

    private EmployeeTrainingResponse mapToEmployeeTrainingResponse(EmployeeTraining record) {
        String employeeName = record.getEmployee().getFirstName() + " " + record.getEmployee().getLastName();

        return new EmployeeTrainingResponse(
            record.getId(),
            record.getEmployee().getId(),
            employeeName,
            record.getTraining().getId(),
            record.getTraining().getName(),
            record.getStatus(),
            record.getDescription(),
            record.getCompletionDate(),
            record.getFeedback()
        );
    }
}
