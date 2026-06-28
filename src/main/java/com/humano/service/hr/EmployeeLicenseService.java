package com.humano.service.hr;

import com.humano.domain.hr.EmployeeLicense;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeLicenseRequest;
import com.humano.dto.hr.requests.UpdateEmployeeLicenseRequest;
import com.humano.dto.hr.responses.EmployeeLicenseResponse;
import com.humano.repository.hr.EmployeeLicenseRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeLicense records owned by an employee.
 */
@Service
public class EmployeeLicenseService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLicenseService.class);

    private final EmployeeLicenseRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeLicenseService(EmployeeLicenseRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeLicenseResponse create(CreateEmployeeLicenseRequest request) {
        log.debug("Request to create EmployeeLicense: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeLicense entity = new EmployeeLicense();
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
    public EmployeeLicenseResponse update(UUID id, UpdateEmployeeLicenseRequest request) {
        log.debug("Request to update EmployeeLicense: {}", id);
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
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeLicense", id));
    }

    @Transactional(readOnly = true)
    public EmployeeLicenseResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmployeeLicense", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeLicenseResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeLicense", id);
        }
        repository.deleteById(id);
    }

    private EmployeeLicenseResponse mapToResponse(EmployeeLicense e) {
        return new EmployeeLicenseResponse(
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
