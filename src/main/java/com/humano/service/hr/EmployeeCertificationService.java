package com.humano.service.hr;

import com.humano.domain.hr.EmployeeCertification;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeCertificationRequest;
import com.humano.dto.hr.requests.UpdateEmployeeCertificationRequest;
import com.humano.dto.hr.responses.EmployeeCertificationResponse;
import com.humano.repository.hr.EmployeeCertificationRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeCertification records owned by an employee.
 */
@Service
public class EmployeeCertificationService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeCertificationService.class);

    private final EmployeeCertificationRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeCertificationService(EmployeeCertificationRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeCertificationResponse create(CreateEmployeeCertificationRequest request) {
        log.debug("Request to create EmployeeCertification: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeCertification entity = new EmployeeCertification();
        entity.setEmployee(employee);
        entity.setName(request.name());
        entity.setIssuer(request.issuer());
        entity.setIssueDate(request.issueDate());
        entity.setExpiryDate(request.expiryDate());
        entity.setVerified(request.verified() != null ? request.verified() : false);
        entity.setDocumentFileId(request.documentFileId());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeCertificationResponse update(UUID id, UpdateEmployeeCertificationRequest request) {
        log.debug("Request to update EmployeeCertification: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.name() != null) {
                    entity.setName(request.name());
                }
                if (request.issuer() != null) {
                    entity.setIssuer(request.issuer());
                }
                if (request.issueDate() != null) {
                    entity.setIssueDate(request.issueDate());
                }
                if (request.expiryDate() != null) {
                    entity.setExpiryDate(request.expiryDate());
                }
                if (request.verified() != null) {
                    entity.setVerified(request.verified());
                }
                if (request.documentFileId() != null) {
                    entity.setDocumentFileId(request.documentFileId());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeCertification", id));
    }

    @Transactional(readOnly = true)
    public EmployeeCertificationResponse getById(UUID id) {
        return repository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeCertification", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeCertificationResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeCertification", id);
        }
        repository.deleteById(id);
    }

    private EmployeeCertificationResponse mapToResponse(EmployeeCertification e) {
        return new EmployeeCertificationResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getName(),
            e.getIssuer(),
            e.getIssueDate(),
            e.getExpiryDate(),
            e.getVerified(),
            e.getDocumentFileId(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
