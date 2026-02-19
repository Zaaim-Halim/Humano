package com.humano.security;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Permission;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.PermissionRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing authority-based permissions.
 * This service provides methods to check if an authority has a specific permission.
 */
@Service
public class AuthorityPermissionService {

    private final Logger log = LoggerFactory.getLogger(AuthorityPermissionService.class);

    private final AuthorityRepository authorityRepository;
    private final PermissionRepository permissionRepository;

    // Cache of authority permissions for improved performance
    private final Map<String, Set<String>> authorityPermissionCache = new HashMap<>();

    public AuthorityPermissionService(AuthorityRepository authorityRepository, PermissionRepository permissionRepository) {
        this.authorityRepository = authorityRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Initialize the permission system and cache authority-permission mappings.
     * This method is called after dependency injection.
     */
    /*@PostConstruct
    @Transactional(readOnly = true)
    public void init() {
        log.debug("Initializing authority permission cache");
        refreshPermissionCache();
        ensureDefaultPermissions();
    }*/

    /**
     * Refresh the authority-permission cache.
     * This should be called whenever authorities or permissions are updated.
     */
    @Transactional(readOnly = true)
    public void refreshPermissionCache() {
        authorityPermissionCache.clear();

        List<Authority> authorities = authorityRepository.findAll();
        for (Authority authority : authorities) {
            Set<String> permissionNames = authority.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
            authorityPermissionCache.put(authority.getName(), permissionNames);
        }

        log.debug("Authority permission cache refreshed with {} authorities", authorities.size());
    }

    /**
     * Ensure that default permissions exist in the database and are assigned to appropriate authorities.
     * This method creates default permissions if they don't exist and assigns them to authorities.
     */
    @Transactional
    public void ensureDefaultPermissions() {
        // Create default permissions if they don't exist
        createPermissionIfNotExists(PermissionsConstants.VIEW_DASHBOARD, "Access to view dashboard");
        createPermissionIfNotExists(PermissionsConstants.ACCESS_API, "Access to use API endpoints");

        // HR permissions
        createPermissionIfNotExists(PermissionsConstants.CREATE_EMPLOYEE, "Create new employee records");
        createPermissionIfNotExists(PermissionsConstants.READ_EMPLOYEE, "View employee records");
        createPermissionIfNotExists(PermissionsConstants.UPDATE_EMPLOYEE, "Update employee records");
        createPermissionIfNotExists(PermissionsConstants.DELETE_EMPLOYEE, "Delete employee records");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_DEPARTMENTS, "Manage department information");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_POSITIONS, "Manage position information");

        // Employee self-service permissions
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_PROFILE, "View own profile information");
        createPermissionIfNotExists(PermissionsConstants.UPDATE_OWN_PROFILE, "Update own profile information");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_PAYSLIPS, "View own payslips");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_TRAINING, "View own training records");
        createPermissionIfNotExists(PermissionsConstants.REGISTER_FOR_TRAINING, "Register for training programs");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_BENEFITS, "View own benefits");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_OWN_BENEFITS, "Manage own benefits");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_LEAVE, "View own leave balances and history");
        createPermissionIfNotExists(PermissionsConstants.REQUEST_LEAVE, "Submit leave requests");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_ATTENDANCE, "View own attendance records");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_PERFORMANCE, "View own performance reviews");
        createPermissionIfNotExists(PermissionsConstants.VIEW_OWN_DOCUMENTS, "View own documents");
        createPermissionIfNotExists(PermissionsConstants.UPLOAD_OWN_DOCUMENTS, "Upload own documents");

        // Payroll permissions
        createPermissionIfNotExists(PermissionsConstants.CREATE_PAYROLL_RUN, "Create a new payroll run");
        createPermissionIfNotExists(PermissionsConstants.VIEW_PAYROLL_RUN, "View payroll run details");
        createPermissionIfNotExists(PermissionsConstants.APPROVE_PAYROLL, "Approve payroll runs");
        createPermissionIfNotExists(PermissionsConstants.PROCESS_PAYROLL, "Process payroll calculations");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_PAY_COMPONENTS, "Manage pay components");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_DEDUCTIONS, "Manage deduction configurations");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_BENEFITS, "Manage employee benefits");
        createPermissionIfNotExists(PermissionsConstants.MANAGE_TAX_BRACKETS, "Manage tax bracket configurations");
        createPermissionIfNotExists(PermissionsConstants.VIEW_PAYSLIPS, "View employee payslips");
        createPermissionIfNotExists(PermissionsConstants.GENERATE_PAYSLIPS, "Generate payslips");

        // Assign default permissions to authorities
        Authority adminAuthority = ensureAuthority(AuthoritiesConstants.ADMIN);
        assignAllPermissionsToAuthority(adminAuthority);

        Authority userAuthority = ensureAuthority(AuthoritiesConstants.USER);
        assignPermissionsToAuthority(userAuthority, Set.of(PermissionsConstants.VIEW_DASHBOARD, PermissionsConstants.ACCESS_API));

        Authority hrManagerAuthority = ensureAuthority(AuthoritiesConstants.HR_MANAGER);
        assignPermissionsToAuthority(
            hrManagerAuthority,
            Set.of(
                PermissionsConstants.VIEW_DASHBOARD,
                PermissionsConstants.ACCESS_API,
                PermissionsConstants.CREATE_EMPLOYEE,
                PermissionsConstants.READ_EMPLOYEE,
                PermissionsConstants.UPDATE_EMPLOYEE,
                PermissionsConstants.DELETE_EMPLOYEE,
                PermissionsConstants.MANAGE_DEPARTMENTS,
                PermissionsConstants.MANAGE_POSITIONS
            )
        );

        Authority hrSpecialistAuthority = ensureAuthority(AuthoritiesConstants.HR_SPECIALIST);
        assignPermissionsToAuthority(
            hrSpecialistAuthority,
            Set.of(
                PermissionsConstants.VIEW_DASHBOARD,
                PermissionsConstants.ACCESS_API,
                PermissionsConstants.READ_EMPLOYEE,
                PermissionsConstants.UPDATE_EMPLOYEE
            )
        );

        Authority payrollAdminAuthority = ensureAuthority(AuthoritiesConstants.PAYROLL_ADMIN);
        assignPermissionsToAuthority(
            payrollAdminAuthority,
            Set.of(
                PermissionsConstants.VIEW_DASHBOARD,
                PermissionsConstants.ACCESS_API,
                PermissionsConstants.READ_EMPLOYEE,
                PermissionsConstants.CREATE_PAYROLL_RUN,
                PermissionsConstants.VIEW_PAYROLL_RUN,
                PermissionsConstants.APPROVE_PAYROLL,
                PermissionsConstants.PROCESS_PAYROLL,
                PermissionsConstants.MANAGE_PAY_COMPONENTS,
                PermissionsConstants.MANAGE_DEDUCTIONS,
                PermissionsConstants.MANAGE_BENEFITS,
                PermissionsConstants.MANAGE_TAX_BRACKETS,
                PermissionsConstants.VIEW_PAYSLIPS,
                PermissionsConstants.GENERATE_PAYSLIPS
            )
        );

        // Create and assign permissions for Employee authority
        Authority employeeAuthority = ensureAuthority(AuthoritiesConstants.EMPLOYEE);
        assignPermissionsToAuthority(
            employeeAuthority,
            Set.of(
                PermissionsConstants.VIEW_DASHBOARD,
                PermissionsConstants.ACCESS_API,
                PermissionsConstants.VIEW_OWN_PROFILE,
                PermissionsConstants.UPDATE_OWN_PROFILE,
                PermissionsConstants.VIEW_OWN_PAYSLIPS,
                PermissionsConstants.VIEW_OWN_TRAINING,
                PermissionsConstants.REGISTER_FOR_TRAINING,
                PermissionsConstants.VIEW_OWN_BENEFITS,
                PermissionsConstants.MANAGE_OWN_BENEFITS,
                PermissionsConstants.VIEW_OWN_LEAVE,
                PermissionsConstants.REQUEST_LEAVE,
                PermissionsConstants.VIEW_OWN_ATTENDANCE,
                PermissionsConstants.VIEW_OWN_PERFORMANCE,
                PermissionsConstants.VIEW_OWN_DOCUMENTS,
                PermissionsConstants.UPLOAD_OWN_DOCUMENTS
            )
        );

        // Refresh cache after making changes
        refreshPermissionCache();
    }

    /**
     * Create a permission if it doesn't already exist in the database.
     *
     * @param name the permission name
     * @param description the permission description
     * @return the permission entity
     */
    @Transactional
    public Permission createPermissionIfNotExists(String name, String description) {
        Optional<Permission> existingPermission = permissionRepository.findById(name);
        if (existingPermission.isPresent()) {
            return existingPermission.get();
        }

        Permission permission = new Permission();
        permission.setName(name);
        permission.setDescription(description);
        return permissionRepository.save(permission);
    }

    /**
     * Ensure that an authority exists in the database.
     *
     * @param name the authority name
     * @return the authority entity
     */
    @Transactional
    public Authority ensureAuthority(String name) {
        Optional<Authority> existingAuthority = authorityRepository.findById(name);
        if (existingAuthority.isPresent()) {
            return existingAuthority.get();
        }

        Authority authority = new Authority();
        authority.setName(name);
        return authorityRepository.save(authority);
    }

    /**
     * Assign a set of permissions to an authority.
     *
     * @param authority the authority entity
     * @param permissionNames the set of permission names to assign
     */
    @Transactional
    public void assignPermissionsToAuthority(Authority authority, Set<String> permissionNames) {
        Set<Permission> existingPermissions = authority.getPermissions();
        Set<String> existingPermissionNames = existingPermissions.stream().map(Permission::getName).collect(Collectors.toSet());

        // Add only permissions that don't already exist
        for (String permissionName : permissionNames) {
            if (!existingPermissionNames.contains(permissionName)) {
                Optional<Permission> permission = permissionRepository.findById(permissionName);
                if (permission.isPresent()) {
                    existingPermissions.add(permission.get());
                } else {
                    log.warn("Permission {} does not exist and cannot be assigned to authority {}", permissionName, authority.getName());
                }
            }
        }

        authority.setPermissions(existingPermissions);
        authorityRepository.save(authority);
    }

    /**
     * Assign all available permissions to an authority.
     *
     * @param authority the authority entity
     */
    @Transactional
    public void assignAllPermissionsToAuthority(Authority authority) {
        List<Permission> allPermissions = permissionRepository.findAll();
        authority.setPermissions(new HashSet<>(allPermissions));
        authorityRepository.save(authority);
    }

    /**
     * Check if an authority has a specific permission.
     *
     * @param authority the authority name to check
     * @param permission the permission name to check for
     * @return true if the authority has the permission, false otherwise
     */
    public boolean hasPermission(String authority, String permission) {
        Set<String> permissions = authorityPermissionCache.get(authority);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Get all permissions for an authority.
     *
     * @param authority the authority name to get permissions for
     * @return a set of permission names for the authority, or an empty set if the authority doesn't exist
     */
    public Set<String> getPermissionsForAuthority(String authority) {
        Set<String> permissions = authorityPermissionCache.get(authority);
        return permissions != null ? Collections.unmodifiableSet(permissions) : Collections.emptySet();
    }

    /**
     * Get all authorities that have a specific permission.
     *
     * @param permission the permission name to find authorities for
     * @return a set of authority names that have the permission
     */
    public Set<String> getAuthoritiesWithPermission(String permission) {
        Set<String> authorities = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : authorityPermissionCache.entrySet()) {
            if (entry.getValue().contains(permission)) {
                authorities.add(entry.getKey());
            }
        }
        return authorities;
    }
}
