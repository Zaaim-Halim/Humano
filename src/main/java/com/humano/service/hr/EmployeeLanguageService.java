package com.humano.service.hr;

import com.humano.domain.hr.EmployeeLanguage;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeLanguageRequest;
import com.humano.dto.hr.requests.UpdateEmployeeLanguageRequest;
import com.humano.dto.hr.responses.EmployeeLanguageResponse;
import com.humano.repository.hr.EmployeeLanguageRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeLanguage records owned by an employee.
 */
@Service
public class EmployeeLanguageService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLanguageService.class);

    private final EmployeeLanguageRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeLanguageService(EmployeeLanguageRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeLanguageResponse create(CreateEmployeeLanguageRequest request) {
        log.debug("Request to create EmployeeLanguage: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeLanguage entity = new EmployeeLanguage();
        entity.setEmployee(employee);
        entity.setLanguage(request.language());
        entity.setReading(request.reading());
        entity.setWriting(request.writing());
        entity.setSpeaking(request.speaking());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeLanguageResponse update(UUID id, UpdateEmployeeLanguageRequest request) {
        log.debug("Request to update EmployeeLanguage: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.language() != null) {
                    entity.setLanguage(request.language());
                }
                if (request.reading() != null) {
                    entity.setReading(request.reading());
                }
                if (request.writing() != null) {
                    entity.setWriting(request.writing());
                }
                if (request.speaking() != null) {
                    entity.setSpeaking(request.speaking());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeLanguage", id));
    }

    @Transactional(readOnly = true)
    public EmployeeLanguageResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmployeeLanguage", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeLanguageResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeLanguage", id);
        }
        repository.deleteById(id);
    }

    private EmployeeLanguageResponse mapToResponse(EmployeeLanguage e) {
        return new EmployeeLanguageResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getLanguage(),
            e.getReading(),
            e.getWriting(),
            e.getSpeaking(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
