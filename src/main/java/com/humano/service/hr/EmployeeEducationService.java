package com.humano.service.hr;

import com.humano.domain.hr.EmployeeEducation;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeEducationRequest;
import com.humano.dto.hr.requests.UpdateEmployeeEducationRequest;
import com.humano.dto.hr.responses.EmployeeEducationResponse;
import com.humano.repository.hr.EmployeeEducationRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeEducation records owned by an employee.
 */
@Service
public class EmployeeEducationService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEducationService.class);

    private final EmployeeEducationRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeEducationService(EmployeeEducationRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeEducationResponse create(CreateEmployeeEducationRequest request) {
        log.debug("Request to create EmployeeEducation: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeEducation entity = new EmployeeEducation();
        entity.setEmployee(employee);
        entity.setInstitution(request.institution());
        entity.setDegree(request.degree());
        entity.setFieldOfStudy(request.fieldOfStudy());
        entity.setGraduationDate(request.graduationDate());
        entity.setDocumentFileId(request.documentFileId());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeEducationResponse update(UUID id, UpdateEmployeeEducationRequest request) {
        log.debug("Request to update EmployeeEducation: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.institution() != null) {
                    entity.setInstitution(request.institution());
                }
                if (request.degree() != null) {
                    entity.setDegree(request.degree());
                }
                if (request.fieldOfStudy() != null) {
                    entity.setFieldOfStudy(request.fieldOfStudy());
                }
                if (request.graduationDate() != null) {
                    entity.setGraduationDate(request.graduationDate());
                }
                if (request.documentFileId() != null) {
                    entity.setDocumentFileId(request.documentFileId());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeEducation", id));
    }

    @Transactional(readOnly = true)
    public EmployeeEducationResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmployeeEducation", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeEducationResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeEducation", id);
        }
        repository.deleteById(id);
    }

    private EmployeeEducationResponse mapToResponse(EmployeeEducation e) {
        return new EmployeeEducationResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getInstitution(),
            e.getDegree(),
            e.getFieldOfStudy(),
            e.getGraduationDate(),
            e.getDocumentFileId(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
