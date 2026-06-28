package com.humano.service.hr;

import com.humano.domain.hr.EmergencyContact;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmergencyContactRequest;
import com.humano.dto.hr.requests.UpdateEmergencyContactRequest;
import com.humano.dto.hr.responses.EmergencyContactResponse;
import com.humano.repository.hr.EmergencyContactRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmergencyContact records owned by an employee.
 */
@Service
public class EmergencyContactService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyContactService.class);

    private final EmergencyContactRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmergencyContactService(EmergencyContactRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmergencyContactResponse create(CreateEmergencyContactRequest request) {
        log.debug("Request to create EmergencyContact: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmergencyContact entity = new EmergencyContact();
        entity.setEmployee(employee);
        entity.setName(request.name());
        entity.setRelationship(request.relationship());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmergencyContactResponse update(UUID id, UpdateEmergencyContactRequest request) {
        log.debug("Request to update EmergencyContact: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.name() != null) {
                    entity.setName(request.name());
                }
                if (request.relationship() != null) {
                    entity.setRelationship(request.relationship());
                }
                if (request.phone() != null) {
                    entity.setPhone(request.phone());
                }
                if (request.email() != null) {
                    entity.setEmail(request.email());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmergencyContact", id));
    }

    @Transactional(readOnly = true)
    public EmergencyContactResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmergencyContact", id));
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmergencyContact", id);
        }
        repository.deleteById(id);
    }

    private EmergencyContactResponse mapToResponse(EmergencyContact e) {
        return new EmergencyContactResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getName(),
            e.getRelationship(),
            e.getPhone(),
            e.getEmail(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
