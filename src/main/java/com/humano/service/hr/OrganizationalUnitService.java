package com.humano.service.hr;

import com.humano.domain.hr.Employee;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreateOrganizationalUnitRequest;
import com.humano.service.hr.dto.requests.UpdateOrganizationalUnitRequest;
import com.humano.service.hr.dto.responses.OrganizationalUnitResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationalUnitService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationalUnitService.class);

    private final OrganizationalUnitRepository organizationalUnitRepository;
    private final EmployeeRepository employeeRepository;

    public OrganizationalUnitService(OrganizationalUnitRepository organizationalUnitRepository, EmployeeRepository employeeRepository) {
        this.organizationalUnitRepository = organizationalUnitRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public OrganizationalUnitResponse createOrganizationalUnit(CreateOrganizationalUnitRequest request) {
        log.debug("Request to create OrganizationalUnit: {}", request);

        OrganizationalUnit unit = new OrganizationalUnit();
        unit.setName(request.name());
        unit.setType(request.type());

        if (request.parentUnitId() != null) {
            OrganizationalUnit parentUnit = organizationalUnitRepository
                .findById(request.parentUnitId())
                .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", request.parentUnitId()));
            unit.setParentUnit(parentUnit);
        }

        if (request.managerId() != null) {
            Employee manager = employeeRepository
                .findById(request.managerId())
                .orElseThrow(() -> EntityNotFoundException.create("Employee", request.managerId()));
            unit.setManager(manager);
        }

        OrganizationalUnit savedUnit = organizationalUnitRepository.save(unit);
        log.info("Created organizational unit with ID: {}", savedUnit.getId());

        return mapToResponse(savedUnit);
    }

    @Transactional
    public OrganizationalUnitResponse updateOrganizationalUnit(UUID id, UpdateOrganizationalUnitRequest request) {
        log.debug("Request to update OrganizationalUnit: {}", id);

        return organizationalUnitRepository
            .findById(id)
            .map(unit -> {
                if (request.name() != null) {
                    unit.setName(request.name());
                }
                if (request.type() != null) {
                    unit.setType(request.type());
                }
                if (request.parentUnitId() != null) {
                    OrganizationalUnit parentUnit = organizationalUnitRepository
                        .findById(request.parentUnitId())
                        .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", request.parentUnitId()));
                    unit.setParentUnit(parentUnit);
                }
                if (request.managerId() != null) {
                    Employee manager = employeeRepository
                        .findById(request.managerId())
                        .orElseThrow(() -> EntityNotFoundException.create("Employee", request.managerId()));
                    unit.setManager(manager);
                }
                return mapToResponse(organizationalUnitRepository.save(unit));
            })
            .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", id));
    }

    @Transactional(readOnly = true)
    public OrganizationalUnitResponse getOrganizationalUnitById(UUID id) {
        log.debug("Request to get OrganizationalUnit by ID: {}", id);

        return organizationalUnitRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", id));
    }

    @Transactional(readOnly = true)
    public Page<OrganizationalUnitResponse> getAllOrganizationalUnits(Pageable pageable) {
        log.debug("Request to get all OrganizationalUnits");

        return organizationalUnitRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationalUnitResponse> getRootOrganizationalUnits(Pageable pageable) {
        log.debug("Request to get root OrganizationalUnits");

        return organizationalUnitRepository.findByParentUnitIsNull(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationalUnitResponse> getSubUnits(UUID parentId, Pageable pageable) {
        log.debug("Request to get sub-units of OrganizationalUnit: {}", parentId);

        return organizationalUnitRepository.findByParentUnitId(parentId, pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deleteOrganizationalUnit(UUID id) {
        log.debug("Request to delete OrganizationalUnit: {}", id);

        if (!organizationalUnitRepository.existsById(id)) {
            throw EntityNotFoundException.create("OrganizationalUnit", id);
        }
        organizationalUnitRepository.deleteById(id);
        log.info("Deleted organizational unit with ID: {}", id);
    }

    private OrganizationalUnitResponse mapToResponse(OrganizationalUnit unit) {
        String managerName = null;
        if (unit.getManager() != null) {
            managerName = unit.getManager().getFirstName() + " " + unit.getManager().getLastName();
        }

        return new OrganizationalUnitResponse(
            unit.getId(),
            unit.getName(),
            unit.getType(),
            unit.getPath(),
            unit.getParentUnit() != null ? unit.getParentUnit().getId() : null,
            unit.getParentUnit() != null ? unit.getParentUnit().getName() : null,
            unit.getManager() != null ? unit.getManager().getId() : null,
            managerName,
            unit.getEmployees() != null ? unit.getEmployees().size() : 0,
            unit.getSubUnits() != null ? unit.getSubUnits().size() : 0,
            unit.getCreatedBy(),
            unit.getCreatedDate(),
            unit.getLastModifiedBy(),
            unit.getLastModifiedDate()
        );
    }
}
