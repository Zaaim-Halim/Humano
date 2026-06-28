package com.humano.service.hr;

import com.humano.domain.hr.EmployeeAsset;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeAssetRequest;
import com.humano.dto.hr.requests.UpdateEmployeeAssetRequest;
import com.humano.dto.hr.responses.EmployeeAssetResponse;
import com.humano.repository.hr.EmployeeAssetRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeAsset records owned by an employee.
 */
@Service
public class EmployeeAssetService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAssetService.class);

    private final EmployeeAssetRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeAssetService(EmployeeAssetRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeAssetResponse create(CreateEmployeeAssetRequest request) {
        log.debug("Request to create EmployeeAsset: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeAsset entity = new EmployeeAsset();
        entity.setEmployee(employee);
        entity.setType(request.type());
        entity.setIdentifier(request.identifier());
        entity.setAssignedDate(request.assignedDate());
        entity.setReturnedDate(request.returnedDate());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeAssetResponse update(UUID id, UpdateEmployeeAssetRequest request) {
        log.debug("Request to update EmployeeAsset: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.type() != null) {
                    entity.setType(request.type());
                }
                if (request.identifier() != null) {
                    entity.setIdentifier(request.identifier());
                }
                if (request.assignedDate() != null) {
                    entity.setAssignedDate(request.assignedDate());
                }
                if (request.returnedDate() != null) {
                    entity.setReturnedDate(request.returnedDate());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeAsset", id));
    }

    @Transactional(readOnly = true)
    public EmployeeAssetResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmployeeAsset", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeAssetResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeAsset", id);
        }
        repository.deleteById(id);
    }

    private EmployeeAssetResponse mapToResponse(EmployeeAsset e) {
        return new EmployeeAssetResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getType(),
            e.getIdentifier(),
            e.getAssignedDate(),
            e.getReturnedDate(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
