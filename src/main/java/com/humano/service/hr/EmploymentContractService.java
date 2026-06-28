package com.humano.service.hr;

import com.humano.domain.hr.Department;
import com.humano.domain.hr.EmploymentContract;
import com.humano.domain.hr.Position;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmploymentContractRequest;
import com.humano.dto.hr.requests.UpdateEmploymentContractRequest;
import com.humano.dto.hr.responses.EmploymentContractResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.EmploymentContractRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmploymentContract records owned by an employee.
 */
@Service
public class EmploymentContractService {

    private static final Logger log = LoggerFactory.getLogger(EmploymentContractService.class);

    private final EmploymentContractRepository repository;
    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;

    public EmploymentContractService(
        EmploymentContractRepository repository,
        EmployeeRepository employeeRepository,
        PositionRepository positionRepository,
        DepartmentRepository departmentRepository
    ) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.positionRepository = positionRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public EmploymentContractResponse create(CreateEmploymentContractRequest request) {
        log.debug("Request to create EmploymentContract: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmploymentContract entity = new EmploymentContract();
        entity.setEmployee(employee);
        entity.setContractNumber(request.contractNumber());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setContractType(request.contractType());
        if (request.positionId() != null) {
            entity.setPosition(
                positionRepository
                    .findById(request.positionId())
                    .orElseThrow(() -> EntityNotFoundException.create("Position", request.positionId()))
            );
        }
        if (request.departmentId() != null) {
            entity.setDepartment(
                departmentRepository
                    .findById(request.departmentId())
                    .orElseThrow(() -> EntityNotFoundException.create("Department", request.departmentId()))
            );
        }
        entity.setWorkingHours(request.workingHours());
        entity.setSignedDate(request.signedDate());
        entity.setStatus(request.status());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmploymentContractResponse update(UUID id, UpdateEmploymentContractRequest request) {
        log.debug("Request to update EmploymentContract: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.contractNumber() != null) {
                    entity.setContractNumber(request.contractNumber());
                }
                if (request.startDate() != null) {
                    entity.setStartDate(request.startDate());
                }
                if (request.endDate() != null) {
                    entity.setEndDate(request.endDate());
                }
                if (request.contractType() != null) {
                    entity.setContractType(request.contractType());
                }
                if (request.positionId() != null) {
                    entity.setPosition(
                        positionRepository
                            .findById(request.positionId())
                            .orElseThrow(() -> EntityNotFoundException.create("Position", request.positionId()))
                    );
                }
                if (request.departmentId() != null) {
                    entity.setDepartment(
                        departmentRepository
                            .findById(request.departmentId())
                            .orElseThrow(() -> EntityNotFoundException.create("Department", request.departmentId()))
                    );
                }
                if (request.workingHours() != null) {
                    entity.setWorkingHours(request.workingHours());
                }
                if (request.signedDate() != null) {
                    entity.setSignedDate(request.signedDate());
                }
                if (request.status() != null) {
                    entity.setStatus(request.status());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmploymentContract", id));
    }

    @Transactional(readOnly = true)
    public EmploymentContractResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("EmploymentContract", id));
    }

    @Transactional(readOnly = true)
    public List<EmploymentContractResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmploymentContract", id);
        }
        repository.deleteById(id);
    }

    private EmploymentContractResponse mapToResponse(EmploymentContract e) {
        return new EmploymentContractResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getContractNumber(),
            e.getStartDate(),
            e.getEndDate(),
            e.getContractType(),
            e.getPosition() != null ? e.getPosition().getId() : null,
            e.getDepartment() != null ? e.getDepartment().getId() : null,
            e.getWorkingHours(),
            e.getSignedDate(),
            e.getStatus(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
