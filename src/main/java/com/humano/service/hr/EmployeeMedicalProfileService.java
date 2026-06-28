package com.humano.service.hr;

import com.humano.domain.hr.EmployeeMedicalProfile;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeMedicalProfileRequest;
import com.humano.dto.hr.requests.UpdateEmployeeMedicalProfileRequest;
import com.humano.dto.hr.responses.EmployeeMedicalProfileResponse;
import com.humano.repository.hr.EmployeeMedicalProfileRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeMedicalProfile records owned by an employee.
 */
@Service
public class EmployeeMedicalProfileService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeMedicalProfileService.class);

    private final EmployeeMedicalProfileRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeMedicalProfileService(EmployeeMedicalProfileRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeMedicalProfileResponse create(CreateEmployeeMedicalProfileRequest request) {
        log.debug("Request to create EmployeeMedicalProfile: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeMedicalProfile entity = new EmployeeMedicalProfile();
        entity.setEmployee(employee);
        entity.setBloodType(request.bloodType());
        entity.setAllergies(request.allergies());
        entity.setEmergencyNotes(request.emergencyNotes());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeMedicalProfileResponse update(UUID id, UpdateEmployeeMedicalProfileRequest request) {
        log.debug("Request to update EmployeeMedicalProfile: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.bloodType() != null) {
                    entity.setBloodType(request.bloodType());
                }
                if (request.allergies() != null) {
                    entity.setAllergies(request.allergies());
                }
                if (request.emergencyNotes() != null) {
                    entity.setEmergencyNotes(request.emergencyNotes());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeMedicalProfile", id));
    }

    @Transactional(readOnly = true)
    public EmployeeMedicalProfileResponse getById(UUID id) {
        return repository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeMedicalProfile", id));
    }

    @Transactional(readOnly = true)
    public EmployeeMedicalProfileResponse getByEmployeeId(UUID employeeId) {
        return repository
            .findByEmployeeId(employeeId)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeMedicalProfile", employeeId));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeMedicalProfile", id);
        }
        repository.deleteById(id);
    }

    private EmployeeMedicalProfileResponse mapToResponse(EmployeeMedicalProfile e) {
        return new EmployeeMedicalProfileResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getBloodType(),
            e.getAllergies(),
            e.getEmergencyNotes(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
