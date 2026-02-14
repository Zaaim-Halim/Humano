package com.humano.service.hr;

import com.humano.domain.Authority;
import com.humano.domain.Country;
import com.humano.domain.User;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.dto.hr.requests.CreateEmployeeProfileRequest;
import com.humano.dto.hr.requests.EmployeeSearchRequest;
import com.humano.dto.hr.requests.UpdateEmployeeProfileRequest;
import com.humano.dto.hr.responses.EmployeeProfileResponse;
import com.humano.dto.hr.responses.SimpleEmployeeProfileResponse;
import com.humano.repository.CountryRepository;
import com.humano.repository.UserRepository;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.hr.specification.EmployeeSpecification;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing employee profiles.
 * This service handles all operations related to employee profiles, excluding document management.
 * It assumes that User entities are created separately upfront.
 *
 * @author halimzaaim
 */
@Service
public class EmployeeProfileService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProfileService.class);
    private static final String ENTITY_NAME = "employee";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final OrganizationalUnitRepository organizationalUnitRepository;
    private final CountryRepository countryRepository;

    public EmployeeProfileService(
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        DepartmentRepository departmentRepository,
        PositionRepository positionRepository,
        OrganizationalUnitRepository organizationalUnitRepository,
        CountryRepository countryRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.organizationalUnitRepository = organizationalUnitRepository;
        this.countryRepository = countryRepository;
    }

    /**
     * Create an employee profile from an existing user.
     *
     * @param userId the ID of the existing user
     * @param request the employee details
     * @return the created employee profile response
     */
    @Transactional
    public EmployeeProfileResponse createEmployeeProfile(UUID userId, CreateEmployeeProfileRequest request) {
        log.debug("Request to create Employee Profile for User ID: {}", userId);

        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        // Check if user is already an employee
        if (employeeRepository.existsById(userId)) {
            throw new BadRequestAlertException("User is already an employee", ENTITY_NAME, "useralreadyemployee");
        }

        // Create employee from existing user
        Employee employee = new Employee();

        // Copy base properties from user
        copyUserPropertiesToEmployee(user, employee);

        // Set employee specific properties
        mapRequestToEmployee(request, employee);
        setEmployeeRelationships(employee, request);

        // Add EMPLOYEE authority
        addEmployeeAuthority(employee);

        // Save the employee
        Employee savedEmployee = employeeRepository.save(employee);
        log.debug("Created employee profile with ID: {}", savedEmployee.getId());

        return mapToEmployeeProfileResponse(savedEmployee);
    }

    /**
     * Update an existing employee profile.
     *
     * @param id the ID of the employee to update
     * @param request the employee details to update
     * @return the updated employee profile response
     */
    @Transactional
    public EmployeeProfileResponse updateEmployeeProfile(UUID id, UpdateEmployeeProfileRequest request) {
        log.debug("Request to update Employee Profile: {}", request);

        return employeeRepository
            .findById(id)
            .map(existingEmployee -> {
                // Update employee properties
                updateEmployeeFromRequest(existingEmployee, request);

                // Update relationships if provided in request
                updateEmployeeRelationships(existingEmployee, request);

                // Save updated employee
                return mapToEmployeeProfileResponse(employeeRepository.save(existingEmployee));
            })
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with ID: " + id));
    }

    /**
     * Get an employee profile by ID.
     *
     * @param id the ID of the employee to retrieve
     * @return the employee profile response
     */
    @Transactional(readOnly = true)
    public EmployeeProfileResponse getEmployeeProfileById(UUID id) {
        log.debug("Request to get Employee Profile by ID: {}", id);

        return employeeRepository
            .findById(id)
            .map(this::mapToEmployeeProfileResponse)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with ID: " + id));
    }

    /**
     * Get all employee profiles with pagination.
     *
     * @param pageable pagination information
     * @return page of employee profile responses
     */
    @Transactional(readOnly = true)
    public Page<SimpleEmployeeProfileResponse> getAllEmployeeProfiles(Pageable pageable) {
        log.debug("Request to get all Employee Profiles");

        return employeeRepository.findAll(pageable).map(this::mapToSimpleEmployeeProfileResponse);
    }

    /**
     * Delete an employee profile.
     * Note: This deletes only the employee record, not the associated user.
     *
     * @param id the ID of the employee to delete
     */
    @Transactional
    public void deleteEmployeeProfile(UUID id) {
        log.debug("Request to delete Employee Profile: {}", id);

        employeeRepository
            .findById(id)
            .ifPresentOrElse(
                employee -> {
                    // Remove EMPLOYEE role
                    removeEmployeeAuthority(employee);
                    employeeRepository.delete(employee);
                },
                () -> {
                    throw new EntityNotFoundException("Employee not found with ID: " + id);
                }
            );
    }

    /**
     * Search employees using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of employee profile responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<SimpleEmployeeProfileResponse> searchEmployees(EmployeeSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Employees with criteria: {}", searchRequest);

        Specification<Employee> specification = EmployeeSpecification.withCriteria(
            searchRequest.firstName(),
            searchRequest.lastName(),
            searchRequest.email(),
            searchRequest.jobTitle(),
            searchRequest.phone(),
            searchRequest.status(),
            searchRequest.departmentId(),
            searchRequest.positionId(),
            searchRequest.unitId(),
            searchRequest.managerId(),
            searchRequest.startDateFrom(),
            searchRequest.startDateTo(),
            searchRequest.endDateFrom(),
            searchRequest.endDateTo()
        );

        return employeeRepository.findAll(specification, pageable).map(this::mapToSimpleEmployeeProfileResponse);
    }

    /**
     * Copy user properties to employee.
     * This method copies only the necessary properties from User to Employee.
     *
     * @param user the source user
     * @param employee the target employee
     */
    private void copyUserPropertiesToEmployee(User user, Employee employee) {
        employee.setId(user.getId());
        employee.setLogin(user.getLogin());
        employee.setPassword(user.getPassword());
        employee.setFirstName(user.getFirstName());
        employee.setLastName(user.getLastName());
        employee.setEmail(user.getEmail());
        employee.setActivated(user.isActivated());
        employee.setLangKey(user.getLangKey());
        employee.setImageUrl(user.getImageUrl());
        employee.setActivationKey(user.getActivationKey());
        employee.setResetKey(user.getResetKey());
        employee.setResetDate(user.getResetDate());
        employee.setAuthorities(user.getAuthorities());
    }

    /**
     * Add EMPLOYEE authority to the employee.
     *
     * @param employee the employee to update
     */
    private void addEmployeeAuthority(Employee employee) {
        Set<Authority> authorities = employee.getAuthorities();
        if (authorities == null) {
            authorities = new HashSet<>();
            employee.setAuthorities(authorities);
        }

        Authority employeeAuthority = new Authority();
        employeeAuthority.setName(AuthoritiesConstants.EMPLOYEE);
        authorities.add(employeeAuthority);
    }

    /**
     * Remove EMPLOYEE authority from the employee.
     *
     * @param employee the employee to update
     */
    private void removeEmployeeAuthority(Employee employee) {
        Set<Authority> authorities = employee.getAuthorities();
        if (authorities != null) {
            authorities.removeIf(auth -> AuthoritiesConstants.EMPLOYEE.equals(auth.getName()));
        }
    }

    /**
     * Set employee relationships from request.
     *
     * @param employee the employee to update
     * @param request the request containing relationship details
     */
    private void setEmployeeRelationships(Employee employee, CreateEmployeeProfileRequest request) {
        // Set required relationships
        Position position = positionRepository
            .findById(request.positionId())
            .orElseThrow(() -> new EntityNotFoundException("Position not found with ID: " + request.positionId()));
        employee.setPosition(position);

        OrganizationalUnit unit = organizationalUnitRepository
            .findById(request.unitId())
            .orElseThrow(() -> new EntityNotFoundException("Organizational Unit not found with ID: " + request.unitId()));
        employee.setUnit(unit);

        // Set optional relationships
        if (request.departmentId() != null) {
            Department department = departmentRepository
                .findById(request.departmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with ID: " + request.departmentId()));
            employee.setDepartment(department);
        }

        if (request.countryId() != null) {
            Country country = countryRepository
                .findById(request.countryId())
                .orElseThrow(() -> new EntityNotFoundException("Country not found with ID: " + request.countryId()));
            employee.setCountry(country);
        }

        if (request.managerId() != null) {
            Employee manager = employeeRepository
                .findById(request.managerId())
                .orElseThrow(() -> new EntityNotFoundException("Manager not found with ID: " + request.managerId()));
            employee.setManager(manager);
        }
    }

    /**
     * Maps the create request to an employee entity.
     *
     * @param request the request containing employee details
     * @param employee the employee entity to populate
     */
    private void mapRequestToEmployee(CreateEmployeeProfileRequest request, Employee employee) {
        employee.setJobTitle(request.jobTitle());
        employee.setPhone(request.phone());
        employee.setStartDate(request.startDate());
        employee.setEndDate(request.endDate());
        employee.setStatus(request.status() != null ? request.status() : EmployeeStatus.ACTIVE);
        employee.setPath(""); // Will be updated by @PrePersist
    }

    /**
     * Updates an employee entity from the update request.
     *
     * @param employee the employee entity to update
     * @param request the request containing updated employee details
     */
    private void updateEmployeeFromRequest(Employee employee, UpdateEmployeeProfileRequest request) {
        if (request.jobTitle() != null) {
            employee.setJobTitle(request.jobTitle());
        }
        if (request.phone() != null) {
            employee.setPhone(request.phone());
        }
        if (request.startDate() != null) {
            employee.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            employee.setEndDate(request.endDate());
        }
        if (request.status() != null) {
            employee.setStatus(request.status());
        }
    }

    /**
     * Updates an employee's relationships from the update request.
     *
     * @param employee the employee entity to update
     * @param request the request containing updated relationship details
     */
    private void updateEmployeeRelationships(Employee employee, UpdateEmployeeProfileRequest request) {
        // Update position if provided
        if (request.positionId() != null) {
            Position position = positionRepository
                .findById(request.positionId())
                .orElseThrow(() -> new EntityNotFoundException("Position not found with ID: " + request.positionId()));
            employee.setPosition(position);
        }

        // Update unit if provided
        if (request.unitId() != null) {
            OrganizationalUnit unit = organizationalUnitRepository
                .findById(request.unitId())
                .orElseThrow(() -> new EntityNotFoundException("Organizational Unit not found with ID: " + request.unitId()));
            employee.setUnit(unit);
        }

        // Update department if provided
        if (request.departmentId() != null) {
            if (request.departmentId().equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
                employee.setDepartment(null);
            } else {
                Department department = departmentRepository
                    .findById(request.departmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with ID: " + request.departmentId()));
                employee.setDepartment(department);
            }
        }

        // Update country if provided
        if (request.countryId() != null) {
            if (request.countryId().equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
                employee.setCountry(null);
            } else {
                Country country = countryRepository
                    .findById(request.countryId())
                    .orElseThrow(() -> new EntityNotFoundException("Country not found with ID: " + request.countryId()));
                employee.setCountry(country);
            }
        }

        // Update manager if provided
        if (request.managerId() != null) {
            if (request.managerId().equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
                employee.setManager(null);
            } else if (request.managerId().equals(employee.getId())) {
                throw new BadRequestAlertException("Employee cannot be their own manager", ENTITY_NAME, "selfReference");
            } else {
                Employee manager = employeeRepository
                    .findById(request.managerId())
                    .orElseThrow(() -> new EntityNotFoundException("Manager not found with ID: " + request.managerId()));
                employee.setManager(manager);
            }
        }
    }

    /**
     * Maps an employee entity to a full employee profile response.
     *
     * @param employee the employee entity
     * @return the employee profile response
     */
    private EmployeeProfileResponse mapToEmployeeProfileResponse(Employee employee) {
        return new EmployeeProfileResponse(
            employee.getId(),
            employee.getJobTitle(),
            employee.getPhone(),
            employee.getStartDate(),
            employee.getEndDate(),
            employee.getStatus(),
            Optional.ofNullable(employee.getCountry()).map(Country::getId).orElse(null),
            Optional.ofNullable(employee.getCountry()).map(Country::getName).orElse(null),
            Optional.ofNullable(employee.getDepartment()).map(Department::getId).orElse(null),
            Optional.ofNullable(employee.getDepartment()).map(Department::getName).orElse(null),
            employee.getPosition().getId(),
            employee.getPosition().getName(),
            employee.getUnit().getId(),
            employee.getUnit().getName(),
            Optional.ofNullable(employee.getManager()).map(Employee::getId).orElse(null),
            Optional.ofNullable(employee.getManager())
                .map(manager -> manager.getJobTitle() + " - " + manager.getPosition().getName())
                .orElse(null),
            employee.getCreatedBy(),
            employee.getCreatedDate(),
            employee.getLastModifiedBy(),
            employee.getLastModifiedDate()
        );
    }

    /**
     * Maps an employee entity to a simplified employee profile response.
     *
     * @param employee the employee entity
     * @return the simple employee profile response
     */
    private SimpleEmployeeProfileResponse mapToSimpleEmployeeProfileResponse(Employee employee) {
        return new SimpleEmployeeProfileResponse(
            employee.getId(),
            employee.getJobTitle(),
            employee.getPhone(),
            employee.getStatus(),
            Optional.ofNullable(employee.getDepartment()).map(Department::getName).orElse(null),
            employee.getPosition().getName()
        );
    }
}
