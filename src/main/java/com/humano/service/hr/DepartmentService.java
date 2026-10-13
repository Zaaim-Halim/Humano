package com.humano.service.hr;

import com.humano.domain.hr.Department;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateDepartmentRequest;
import com.humano.dto.hr.requests.UpdateDepartmentRequest;
import com.humano.dto.hr.responses.DepartmentResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.projection.EmployeeCountRow;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
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
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional("tenantTransactionManager")
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        log.debug("Request to create Department: {}", request);

        Department department = new Department();
        department.setName(request.name());
        department.setDescription(request.description());

        if (request.headId() != null) {
            Employee head = employeeRepository
                .findById(request.headId())
                .orElseThrow(() -> EntityNotFoundException.create("Employee", request.headId()));
            department.setHead(head);
        }

        Department savedDepartment = departmentRepository.save(department);
        log.info("Created department with ID: {}", savedDepartment.getId());

        return mapToResponse(savedDepartment);
    }

    @Transactional("tenantTransactionManager")
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
                if (request.headId() != null) {
                    Employee head = employeeRepository
                        .findById(request.headId())
                        .orElseThrow(() -> EntityNotFoundException.create("Employee", request.headId()));
                    department.setHead(head);
                }
                return mapToResponse(departmentRepository.save(department));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Department", id));
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public DepartmentResponse getDepartmentById(UUID id) {
        log.debug("Request to get Department by ID: {}", id);

        return departmentRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Department", id));
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<DepartmentResponse> getAllDepartments(Pageable pageable) {
        log.debug("Request to get all Departments");

        // One GROUP BY for every department's headcount instead of loading each employee collection.
        Map<UUID, Long> counts = new HashMap<>();
        for (EmployeeCountRow row : employeeRepository.countByDepartment()) {
            if (row.key() != null) {
                counts.put((UUID) row.key(), row.count());
            }
        }
        return departmentRepository.findAll(pageable).map(d -> mapToResponse(d, counts.getOrDefault(d.getId(), 0L)));
    }

    @Transactional("tenantTransactionManager")
    public void deleteDepartment(UUID id) {
        log.debug("Request to delete Department: {}", id);

        if (!departmentRepository.existsById(id)) {
            throw EntityNotFoundException.create("Department", id);
        }
        departmentRepository.deleteById(id);
        log.info("Deleted department with ID: {}", id);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public boolean existsByName(String name) {
        return departmentRepository.existsByName(name);
    }

    /**
     * Assign a head/manager to a department.
     *
     * @param departmentId the department ID
     * @param headId the employee ID to assign as head
     * @return the updated department response
     */
    @Transactional("tenantTransactionManager")
    public DepartmentResponse assignHead(UUID departmentId, UUID headId) {
        log.debug("Request to assign head {} to Department {}", headId, departmentId);

        Department department = departmentRepository
            .findById(departmentId)
            .orElseThrow(() -> EntityNotFoundException.create("Department", departmentId));

        Employee head = employeeRepository.findById(headId).orElseThrow(() -> EntityNotFoundException.create("Employee", headId));

        department.setHead(head);
        Department savedDepartment = departmentRepository.save(department);
        log.info("Assigned head {} to department {}", headId, departmentId);

        return mapToResponse(savedDepartment);
    }

    /**
     * Remove the head/manager from a department.
     *
     * @param departmentId the department ID
     * @return the updated department response
     */
    @Transactional("tenantTransactionManager")
    public DepartmentResponse removeHead(UUID departmentId) {
        log.debug("Request to remove head from Department {}", departmentId);

        Department department = departmentRepository
            .findById(departmentId)
            .orElseThrow(() -> EntityNotFoundException.create("Department", departmentId));

        department.setHead(null);
        Department savedDepartment = departmentRepository.save(department);
        log.info("Removed head from department {}", departmentId);

        return mapToResponse(savedDepartment);
    }

    private DepartmentResponse mapToResponse(Department department) {
        return mapToResponse(department, department.getId() == null ? 0 : employeeRepository.countByDepartmentId(department.getId()));
    }

    private DepartmentResponse mapToResponse(Department department, long employeeCount) {
        Employee head = department.getHead();
        return new DepartmentResponse(
            department.getId(),
            department.getName(),
            department.getDescription(),
            head != null ? head.getId() : null,
            head != null ? head.getFirstName() + " " + head.getLastName() : null,
            (int) employeeCount,
            department.getCreatedBy(),
            department.getCreatedDate(),
            department.getLastModifiedBy(),
            department.getLastModifiedDate()
        );
    }
}
