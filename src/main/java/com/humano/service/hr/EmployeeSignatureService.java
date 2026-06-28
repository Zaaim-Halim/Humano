package com.humano.service.hr;

import com.humano.domain.hr.EmployeeSignature;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeSignatureRequest;
import com.humano.dto.hr.requests.UpdateEmployeeSignatureRequest;
import com.humano.dto.hr.responses.EmployeeSignatureResponse;
import com.humano.repository.hr.EmployeeSignatureRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeSignature records owned by an employee.
 */
@Service
public class EmployeeSignatureService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSignatureService.class);

    private final EmployeeSignatureRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeSignatureService(EmployeeSignatureRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeSignatureResponse create(CreateEmployeeSignatureRequest request) {
        log.debug("Request to create EmployeeSignature: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeSignature entity = new EmployeeSignature();
        entity.setEmployee(employee);
        entity.setSignatureFileId(request.signatureFileId());
        entity.setCertificate(request.certificate());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeSignatureResponse update(UUID id, UpdateEmployeeSignatureRequest request) {
        log.debug("Request to update EmployeeSignature: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.signatureFileId() != null) {
                    entity.setSignatureFileId(request.signatureFileId());
                }
                if (request.certificate() != null) {
                    entity.setCertificate(request.certificate());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeSignature", id));
    }

    @Transactional(readOnly = true)
    public EmployeeSignatureResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmployeeSignature", id));
    }

    @Transactional(readOnly = true)
    public EmployeeSignatureResponse getByEmployeeId(UUID employeeId) {
        return repository
            .findByEmployeeId(employeeId)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeSignature", employeeId));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeSignature", id);
        }
        repository.deleteById(id);
    }

    private EmployeeSignatureResponse mapToResponse(EmployeeSignature e) {
        return new EmployeeSignatureResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getSignatureFileId(),
            e.getCertificate(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
