package com.humano.service.hr;

import com.humano.domain.hr.WorkPermit;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateWorkPermitRequest;
import com.humano.dto.hr.requests.UpdateWorkPermitRequest;
import com.humano.dto.hr.responses.WorkPermitResponse;
import com.humano.repository.hr.WorkPermitRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing WorkPermit records owned by an employee.
 */
@Service
public class WorkPermitService {

    private static final Logger log = LoggerFactory.getLogger(WorkPermitService.class);

    private final WorkPermitRepository repository;
    private final EmployeeRepository employeeRepository;

    public WorkPermitService(WorkPermitRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public WorkPermitResponse create(CreateWorkPermitRequest request) {
        log.debug("Request to create WorkPermit: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        WorkPermit entity = new WorkPermit();
        entity.setEmployee(employee);
        entity.setVisaType(request.visaType());
        entity.setPermitNumber(request.permitNumber());
        entity.setIssueDate(request.issueDate());
        entity.setExpiryDate(request.expiryDate());
        entity.setSponsor(request.sponsor());
        entity.setDocumentFileId(request.documentFileId());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public WorkPermitResponse update(UUID id, UpdateWorkPermitRequest request) {
        log.debug("Request to update WorkPermit: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.visaType() != null) {
                    entity.setVisaType(request.visaType());
                }
                if (request.permitNumber() != null) {
                    entity.setPermitNumber(request.permitNumber());
                }
                if (request.issueDate() != null) {
                    entity.setIssueDate(request.issueDate());
                }
                if (request.expiryDate() != null) {
                    entity.setExpiryDate(request.expiryDate());
                }
                if (request.sponsor() != null) {
                    entity.setSponsor(request.sponsor());
                }
                if (request.documentFileId() != null) {
                    entity.setDocumentFileId(request.documentFileId());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("WorkPermit", id));
    }

    @Transactional(readOnly = true)
    public WorkPermitResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("WorkPermit", id));
    }

    @Transactional(readOnly = true)
    public List<WorkPermitResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("WorkPermit", id);
        }
        repository.deleteById(id);
    }

    private WorkPermitResponse mapToResponse(WorkPermit e) {
        return new WorkPermitResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getVisaType(),
            e.getPermitNumber(),
            e.getIssueDate(),
            e.getExpiryDate(),
            e.getSponsor(),
            e.getDocumentFileId(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
