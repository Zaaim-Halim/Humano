package com.humano.service.hr;

import com.humano.domain.hr.Department;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreateDepartmentRequest;
import com.humano.service.hr.dto.requests.UpdateDepartmentRequest;
import com.humano.service.hr.dto.responses.DepartmentResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        log.debug("Request to create Department: {}", request);

        Department department = new Department();
        department.setName(request.name());
        department.setDescription(request.description());

        Department savedDepartment = departmentRepository.save(department);
        log.info("Created department with ID: {}", savedDepartment.getId());

        return mapToResponse(savedDepartment);
    }

    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request) {
        log.debug("Request to update Department: {}", id);

        return departmentRepository
            .findById(id)
            .map(department -> {
                if (request.name() != null) {
                    department.setName(request.name());
                }
                if (request.description() != null) {
                    department.setDescription(request.description());
                }
                return mapToResponse(departmentRepository.save(department));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Department", id));
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(UUID id) {
        log.debug("Request to get Department by ID: {}", id);

        return departmentRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Department", id));
    }

    @Transactional(readOnly = true)
    public Page<DepartmentResponse> getAllDepartments(Pageable pageable) {
        log.debug("Request to get all Departments");

        return departmentRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        log.debug("Request to delete Department: {}", id);

        if (!departmentRepository.existsById(id)) {
            throw EntityNotFoundException.create("Department", id);
        }
        departmentRepository.deleteById(id);
        log.info("Deleted department with ID: {}", id);
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return departmentRepository.existsByName(name);
    }

    private DepartmentResponse mapToResponse(Department department) {
        return new DepartmentResponse(
            department.getId(),
            department.getName(),
            department.getDescription(),
            department.getEmployees() != null ? department.getEmployees().size() : 0,
            department.getCreatedBy(),
            department.getCreatedDate(),
            department.getLastModifiedBy(),
            department.getLastModifiedDate()
        );
    }
}
