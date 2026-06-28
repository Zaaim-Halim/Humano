package com.humano.service.hr;

import com.humano.domain.hr.EmployeeExperience;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeExperienceRequest;
import com.humano.dto.hr.requests.UpdateEmployeeExperienceRequest;
import com.humano.dto.hr.responses.EmployeeExperienceResponse;
import com.humano.repository.hr.EmployeeExperienceRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeExperience records owned by an employee.
 */
@Service
public class EmployeeExperienceService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeExperienceService.class);

    private final EmployeeExperienceRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeExperienceService(EmployeeExperienceRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeExperienceResponse create(CreateEmployeeExperienceRequest request) {
        log.debug("Request to create EmployeeExperience: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeExperience entity = new EmployeeExperience();
        entity.setEmployee(employee);
        entity.setCompany(request.company());
        entity.setPosition(request.position());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeExperienceResponse update(UUID id, UpdateEmployeeExperienceRequest request) {
        log.debug("Request to update EmployeeExperience: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.company() != null) {
                    entity.setCompany(request.company());
                }
                if (request.position() != null) {
                    entity.setPosition(request.position());
                }
                if (request.startDate() != null) {
                    entity.setStartDate(request.startDate());
                }
                if (request.endDate() != null) {
                    entity.setEndDate(request.endDate());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeExperience", id));
    }

    @Transactional(readOnly = true)
    public EmployeeExperienceResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmployeeExperience", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeExperienceResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeExperience", id);
        }
        repository.deleteById(id);
    }

    private EmployeeExperienceResponse mapToResponse(EmployeeExperience e) {
        return new EmployeeExperienceResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getCompany(),
            e.getPosition(),
            e.getStartDate(),
            e.getEndDate(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
