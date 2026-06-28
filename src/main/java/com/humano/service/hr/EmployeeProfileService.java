package com.humano.service.hr;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Country;
import com.humano.domain.shared.Employee;
import com.humano.domain.shared.User;
import com.humano.dto.hr.requests.CreateEmployeeProfileRequest;
import com.humano.dto.hr.requests.CreateEmployeeRequest;
import com.humano.dto.hr.requests.EmployeeSearchRequest;
import com.humano.dto.hr.requests.UpdateEmployeeProfileRequest;
import com.humano.dto.hr.responses.CountryRef;
import com.humano.dto.hr.responses.EmployeeProfileResponse;
import com.humano.dto.hr.responses.ReferenceDataRef;
import com.humano.dto.hr.responses.SimpleEmployeeProfileResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.EmployeeCategoryRepository;
import com.humano.repository.hr.EmploymentTypeRepository;
import com.humano.repository.hr.JobGradeRepository;
import com.humano.repository.hr.JobLevelRepository;
import com.humano.repository.hr.MaritalStatusRepository;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.hr.TerminationReasonRepository;
import com.humano.repository.hr.specification.EmployeeSpecification;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.MailService;
import com.humano.service.admin.UserAccountService;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import com.humano.web.rest.errors.EmailAlreadyUsedException;
import com.humano.web.rest.errors.LoginAlreadyUsedException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
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
    private final AuthorityRepository authorityRepository;
    private final MaritalStatusRepository maritalStatusRepository;
    private final EmploymentTypeRepository employmentTypeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobLevelRepository jobLevelRepository;
    private final EmployeeCategoryRepository employeeCategoryRepository;
    private final TerminationReasonRepository terminationReasonRepository;
    private final UserAccountService userAccountService;
    private final MailService mailService;

    @PersistenceContext
    private EntityManager entityManager;

    public EmployeeProfileService(
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        DepartmentRepository departmentRepository,
        PositionRepository positionRepository,
        OrganizationalUnitRepository organizationalUnitRepository,
        CountryRepository countryRepository,
        AuthorityRepository authorityRepository,
        MaritalStatusRepository maritalStatusRepository,
        EmploymentTypeRepository employmentTypeRepository,
        JobGradeRepository jobGradeRepository,
        JobLevelRepository jobLevelRepository,
        EmployeeCategoryRepository employeeCategoryRepository,
        TerminationReasonRepository terminationReasonRepository,
        UserAccountService userAccountService,
        MailService mailService
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.organizationalUnitRepository = organizationalUnitRepository;
        this.countryRepository = countryRepository;
        this.authorityRepository = authorityRepository;
        this.maritalStatusRepository = maritalStatusRepository;
        this.employmentTypeRepository = employmentTypeRepository;
        this.jobGradeRepository = jobGradeRepository;
        this.jobLevelRepository = jobLevelRepository;
        this.employeeCategoryRepository = employeeCategoryRepository;
        this.terminationReasonRepository = terminationReasonRepository;
        this.userAccountService = userAccountService;
        this.mailService = mailService;
    }

    /**
     * Provision a brand-new employee in a single step: create the backing user
     * account, send the activation/creation email, then attach the HR profile.
     * <p>
     * There is no separate "user management" surface — in an HR/Payroll tenant
     * everyone is an employee, and accounts are always created by an authorized
     * person (never self-registered). The recipient sets their own password via
     * the creation email's reset link.
     *
     * @param request the combined account + profile details
     * @return the created employee profile response
     */
    @Transactional
    public EmployeeProfileResponse provisionEmployee(CreateEmployeeRequest request) {
        log.debug("Request to provision Employee with login: {}", request.login());

        // Reject collisions up-front (mirrors UserAdminResource.createUser).
        if (userRepository.findOneByLogin(request.login().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        }
        if (request.email() != null && userRepository.findOneByEmailIgnoreCase(request.email()).isPresent()) {
            throw new EmailAlreadyUsedException();
        }

        // Create the auth account (random password + reset key) and notify the recipient.
        User user = userAccountService.createUser(request.toCreateUserRequest());
        mailService.sendCreationEmail(user);

        // Employee shares the user's id (JOINED inheritance). Detaching here mirrors
        // the proven path where the user already exists in a prior persistence
        // context, so persisting the Employee subclass row can't collide with the
        // just-created managed User of the same id.
        entityManager.flush();
        entityManager.clear();

        User attached = userRepository
            .findById(user.getId())
            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + user.getId()));

        return attachProfile(attached, request.toProfileRequest());
    }

    /**
     * Turn an existing user into an employee by inserting the JOINED-inheritance
     * child row and populating the HR profile.
     */
    private EmployeeProfileResponse attachProfile(User user, CreateEmployeeProfileRequest request) {
        if (employeeRepository.existsById(user.getId())) {
            throw new BadRequestAlertException("User is already an employee", ENTITY_NAME, "useralreadyemployee");
        }

        Employee employee = new Employee();
        copyUserPropertiesToEmployee(user, employee);
        mapRequestToEmployee(request, employee);
        setEmployeeRelationships(employee, request);
        addEmployeeAuthority(employee);

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
                String oldPath = existingEmployee.getPath();

                updateEmployeeFromRequest(existingEmployee, request);
                updateEmployeeRelationships(existingEmployee, request);
                updateEmployeeAuthorities(existingEmployee, request.authorities());

                // saveAndFlush so the cursor row's recomputed path is in the DB before we
                // bulk-rewrite the descendants. The @PreUpdate hook on Employee recomputes
                // path from the (possibly new) manager.
                Employee saved = employeeRepository.saveAndFlush(existingEmployee);
                String newPath = saved.getPath();
                if (oldPath != null && !oldPath.equals(newPath)) {
                    int rewritten = employeeRepository.rewriteDescendantPaths(oldPath, newPath);
                    log.info("Rewrote {} descendant path(s) after reparenting employee {}", rewritten, id);
                }

                return mapToEmployeeProfileResponse(saved);
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
     * The role/authority names assignable to an employee on the create/edit form.
     *
     * @return all authority names known to the tenant
     */
    @Transactional(readOnly = true)
    public java.util.List<String> getAssignableRoles() {
        return userAccountService.getAuthorities();
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

        if (searchRequest.query() != null && !searchRequest.query().isBlank()) {
            specification = specification.and(EmployeeSpecification.matchesNameOrJobTitle(searchRequest.query()));
        }

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
     * Full-replacement of an employee's granted roles. The {@code EMPLOYEE}
     * authority is always re-added so an employee never loses their base role.
     * A {@code null} set means "leave roles untouched".
     *
     * @param employee the employee to update
     * @param authorityNames the requested role names, or {@code null} to keep existing
     */
    private void updateEmployeeAuthorities(Employee employee, Set<String> authorityNames) {
        if (authorityNames == null) {
            return;
        }
        Set<Authority> managed = employee.getAuthorities();
        if (managed == null) {
            managed = new HashSet<>();
            employee.setAuthorities(managed);
        }
        managed.clear();
        authorityNames.stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get).forEach(managed::add);
        addEmployeeAuthority(employee);
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

        // Reference-data relationships (nested refs; only id is read). On create, an omitted ref
        // leaves the relationship null.
        employee.setNationality(resolveCountry(request.nationality()));
        employee.setMaritalStatus(resolveRef(request.maritalStatus(), maritalStatusRepository, "MaritalStatus"));
        employee.setEmploymentType(resolveRef(request.employmentType(), employmentTypeRepository, "EmploymentType"));
        employee.setGrade(resolveRef(request.grade(), jobGradeRepository, "JobGrade"));
        employee.setLevel(resolveRef(request.level(), jobLevelRepository, "JobLevel"));
        employee.setCategory(resolveRef(request.category(), employeeCategoryRepository, "EmployeeCategory"));
        employee.setTerminationReason(resolveRef(request.terminationReason(), terminationReasonRepository, "TerminationReason"));
    }

    /**
     * Resolves a nested reference-data ref to its entity, or null when the ref (or its id) is
     * absent. Throws when an id is given but no matching row exists.
     */
    private <T> T resolveRef(ReferenceDataRef ref, JpaRepository<T, UUID> repository, String name) {
        if (ref == null || ref.id() == null) {
            return null;
        }
        return repository.findById(ref.id()).orElseThrow(() -> new EntityNotFoundException(name + " not found with ID: " + ref.id()));
    }

    /** Resolves a nested country ref to its entity, or null when absent. */
    private Country resolveCountry(CountryRef ref) {
        if (ref == null || ref.id() == null) {
            return null;
        }
        return countryRepository
            .findById(ref.id())
            .orElseThrow(() -> new EntityNotFoundException("Country not found with ID: " + ref.id()));
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
        if (request.employeeNumber() != null) {
            employee.setEmployeeNumber(request.employeeNumber());
        }
        if (request.birthDate() != null) {
            employee.setBirthDate(request.birthDate());
        }
        if (request.gender() != null) {
            employee.setGender(request.gender());
        }
        if (request.placeOfBirth() != null) {
            employee.setPlaceOfBirth(request.placeOfBirth());
        }
        if (request.workPhone() != null) {
            employee.setWorkPhone(request.workPhone());
        }
        if (request.workLocation() != null) {
            employee.setWorkLocation(request.workLocation());
        }
        if (request.fte() != null) {
            employee.setFte(request.fte());
        }
        if (request.probationEndDate() != null) {
            employee.setProbationEndDate(request.probationEndDate());
        }
        if (request.confirmationDate() != null) {
            employee.setConfirmationDate(request.confirmationDate());
        }
        if (request.terminationNotes() != null) {
            employee.setTerminationNotes(request.terminationNotes());
        }
        if (request.nationalId() != null) {
            employee.setNationalId(request.nationalId());
        }
        if (request.passportNumber() != null) {
            employee.setPassportNumber(request.passportNumber());
        }
        if (request.taxNumber() != null) {
            employee.setTaxNumber(request.taxNumber());
        }
        if (request.socialSecurityNumber() != null) {
            employee.setSocialSecurityNumber(request.socialSecurityNumber());
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
                rejectIfWouldCreateCycle(employee, manager);
                employee.setManager(manager);
            }
        }

        // Update reference-data relationships when a nested ref is provided (omitted = unchanged).
        if (request.nationality() != null) {
            employee.setNationality(resolveCountry(request.nationality()));
        }
        if (request.maritalStatus() != null) {
            employee.setMaritalStatus(resolveRef(request.maritalStatus(), maritalStatusRepository, "MaritalStatus"));
        }
        if (request.employmentType() != null) {
            employee.setEmploymentType(resolveRef(request.employmentType(), employmentTypeRepository, "EmploymentType"));
        }
        if (request.grade() != null) {
            employee.setGrade(resolveRef(request.grade(), jobGradeRepository, "JobGrade"));
        }
        if (request.level() != null) {
            employee.setLevel(resolveRef(request.level(), jobLevelRepository, "JobLevel"));
        }
        if (request.category() != null) {
            employee.setCategory(resolveRef(request.category(), employeeCategoryRepository, "EmployeeCategory"));
        }
        if (request.terminationReason() != null) {
            employee.setTerminationReason(resolveRef(request.terminationReason(), terminationReasonRepository, "TerminationReason"));
        }
    }

    /**
     * Re-parenting an employee under one of their own subordinates would create a
     * cycle and silently corrupt the materialized path. A subordinate's path always
     * begins with the manager's path followed by a slash, so that prefix check
     * detects every cyclic case in one comparison.
     */
    private void rejectIfWouldCreateCycle(Employee employee, Employee proposedManager) {
        String employeePath = employee.getPath();
        String managerPath = proposedManager.getPath();
        if (employeePath == null || managerPath == null) {
            return;
        }
        if (managerPath.equals(employeePath) || managerPath.startsWith(employeePath + "/")) {
            throw new BadRequestAlertException(
                "New manager cannot be the employee themselves or one of their subordinates",
                ENTITY_NAME,
                "cyclicHierarchy"
            );
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
            Optional.ofNullable(employee.getAuthorities())
                .orElseGet(Set::of)
                .stream()
                .map(Authority::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
            employee.getEmployeeNumber(),
            employee.getBirthDate(),
            employee.getGender(),
            employee.getPlaceOfBirth(),
            employee.getWorkPhone(),
            employee.getWorkLocation(),
            employee.getFte(),
            employee.getProbationEndDate(),
            employee.getConfirmationDate(),
            employee.getTerminationNotes(),
            employee.getNationalId(),
            employee.getPassportNumber(),
            employee.getTaxNumber(),
            employee.getSocialSecurityNumber(),
            CountryRef.of(employee.getNationality()),
            ReferenceDataRef.of(employee.getMaritalStatus()),
            ReferenceDataRef.of(employee.getEmploymentType()),
            ReferenceDataRef.of(employee.getGrade()),
            ReferenceDataRef.of(employee.getLevel()),
            ReferenceDataRef.of(employee.getCategory()),
            ReferenceDataRef.of(employee.getTerminationReason()),
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
            employee.getFirstName(),
            employee.getLastName(),
            employee.getJobTitle(),
            employee.getPhone(),
            employee.getStatus(),
            Optional.ofNullable(employee.getDepartment()).map(Department::getName).orElse(null),
            employee.getPosition().getName()
        );
    }
}
