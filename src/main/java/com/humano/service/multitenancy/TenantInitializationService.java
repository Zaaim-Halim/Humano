package com.humano.service.multitenancy;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.enumeration.hr.ApproverType;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.hr.ApprovalChainConfig;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Permission;
import com.humano.domain.shared.User;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.hr.workflow.ApprovalChainConfigRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.PermissionRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.security.AuthorityPermissionService;
import com.humano.security.DefaultRolePermissions;
import com.humano.security.PlatformRolePermissions;
import com.humano.service.hr.workflow.ApprovalChainValidator;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds a freshly-migrated tenant database with the data required for the tenant admin to
 * sign in and begin operating: default roles + permissions, the admin user, and minimum
 * payroll reference data .
 *
 * <p>All writes go to the tenant DB. We bracket the work with
 * {@link TenantContext#setCurrentTenant} so {@code TenantRoutingDataSource} resolves to
 * the right Hikari pool, and drive commits via a {@link TransactionTemplate} bound to
 * {@code tenantTransactionManager} (the master TM that wraps {@code provisionTenant} is
 * not appropriate for these writes per invariant I1).
 */
@Service
public class TenantInitializationService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantInitializationService.class);

    /**
     * Roles + permissions seeded for every new tenant come from the single source of truth
     * {@link DefaultRolePermissions} (and {@link PlatformRolePermissions} for the platform
     * tenant). This anchors seeding to the same {@code AuthoritiesConstants}/
     * {@code PermissionsConstants} the {@code @RequirePermission} gates check, eliminating the
     * historical drift where seeded codes never matched gated codes.
     */

    /**
     * Default approval chains seeded for every new tenant . Two-step DIRECT_MANAGER →
     * DEPARTMENT_HEAD for the three workflows the orchestrator currently supports
     * end-to-end. Other {@link ApprovalType} values (TRAINING_REQUEST, POSITION_TRANSFER,
     * SALARY_ADJUSTMENT, TIMESHEET_APPROVAL) are deliberately left empty so they fail loud
     * on first use until a tenant deliberately configures them — silently auto-approving
     * a salary adjustment would be worse than a clear "no chain configured" error.
     */
    private static final List<ApproverType> DEFAULT_CHAIN_STEPS = List.of(ApproverType.DIRECT_MANAGER, ApproverType.DEPARTMENT_HEAD);

    private static final List<ApprovalType> DEFAULT_CHAIN_TYPES = List.of(
        ApprovalType.LEAVE_REQUEST,
        ApprovalType.EXPENSE_CLAIM,
        ApprovalType.OVERTIME_REQUEST
    );

    private final AuthorityRepository authorityRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PayrollCalendarRepository payrollCalendarRepository;
    private final PayComponentRepository payComponentRepository;
    private final ApprovalChainConfigRepository approvalChainConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final MultiTenantProperties multiTenantProperties;
    private final AuthorityPermissionService authorityPermissionService;
    private final TransactionTemplate tenantTx;

    public TenantInitializationService(
        AuthorityRepository authorityRepository,
        PermissionRepository permissionRepository,
        UserRepository userRepository,
        PayrollCalendarRepository payrollCalendarRepository,
        PayComponentRepository payComponentRepository,
        ApprovalChainConfigRepository approvalChainConfigRepository,
        PasswordEncoder passwordEncoder,
        MultiTenantProperties multiTenantProperties,
        AuthorityPermissionService authorityPermissionService,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.authorityRepository = authorityRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.payrollCalendarRepository = payrollCalendarRepository;
        this.payComponentRepository = payComponentRepository;
        this.approvalChainConfigRepository = approvalChainConfigRepository;
        this.passwordEncoder = passwordEncoder;
        this.multiTenantProperties = multiTenantProperties;
        this.authorityPermissionService = authorityPermissionService;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    /**
     * Seed the tenant DB. Idempotent: existing roles/permissions/users/calendars/components
     * are left in place, so it's safe to re-run on a partially-provisioned tenant .
     */
    public void initializeTenant(Tenant tenant, TenantRegistrationDTO registration) {
        LOG.info("Initializing tenant '{}' with default data", tenant.getSubdomain());

        String previousTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(tenant.getSubdomain());
        try {
            Map<String, Set<String>> rolePermissions = effectiveRolePermissions(tenant);
            tenantTx.executeWithoutResult(status -> {
                Map<String, Authority> roles = seedAuthorities(rolePermissions.keySet());
                seedPermissions(rolePermissions, roles);
                seedAdminUser(tenant, registration, roles);
                seedDefaultConfiguration(tenant);
            });
            LOG.info("Successfully initialized tenant '{}'", tenant.getSubdomain());
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Backfill the role/permission catalog for an <em>already-provisioned</em> tenant, plus —
     * for the platform tenant only — grant {@link AuthoritiesConstants#PLATFORM_OWNER} to
     * existing platform admins. Idempotent and <strong>additive only</strong> (never removes a
     * role/permission/grant), so it is safe to run on every boot.
     * <p>
     * Why this exists: tenants provisioned before the catalog/seeder unification hold only the
     * legacy role/permission set; without backfill, finer-grained permission checks resolve
     * against an empty catalog and the new platform gates would 403 the platform admin. Run
     * after the tenant's schema migration so the {@code authority}/{@code permission} tables
     * exist (see {@code StartupTenantMigrator}).
     */
    public void backfillRolesAndPermissions(Tenant tenant) {
        String previousTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(tenant.getSubdomain());
        try {
            Map<String, Set<String>> rolePermissions = effectiveRolePermissions(tenant);
            tenantTx.executeWithoutResult(status -> {
                // (1) Behaviour-neutral, additive: ensure the full catalog exists. Nothing holds
                // the new roles and nothing checks the new permissions yet, so this cannot change
                // any access decision on its own.
                Map<String, Authority> roles = seedAuthorities(rolePermissions.keySet());
                seedPermissions(rolePermissions, roles);

                // (2) The sharp edge — a user-authority grant — strictly bounded to the platform
                // tenant. If the platformTenant property were ever misconfigured to a business
                // tenant this would escalate a business admin, so the guard is the only thing
                // standing between safe and a cross-tenant breach; keep it additive and loud.
                if (isPlatformTenant(tenant)) {
                    int granted = applyPlatformOwnerGrant(userRepository.findAll(), roles.get(AuthoritiesConstants.PLATFORM_OWNER));
                    if (granted > 0) {
                        LOG.warn(
                            "Backfill: granted {} to {} existing admin user(s) in platform tenant '{}'",
                            AuthoritiesConstants.PLATFORM_OWNER,
                            granted,
                            tenant.getSubdomain()
                        );
                    }
                }
            });
            // The per-tenant permission cache is lazy; evict in case it was already warmed.
            authorityPermissionService.evictCurrentTenant();
            LOG.info("Backfilled roles/permissions for tenant '{}'", tenant.getSubdomain());
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Additively grant {@code ownerRole} to every user that already holds
     * {@link AuthoritiesConstants#ADMIN} but not the owner role. Package-private and free of
     * tenant/transaction concerns so the grant policy can be unit-tested in isolation — this is
     * the security-sensitive decision the caller bounds to the platform tenant.
     *
     * @return the number of users granted the owner role
     */
    int applyPlatformOwnerGrant(Iterable<User> users, Authority ownerRole) {
        if (ownerRole == null) {
            return 0;
        }
        int granted = 0;
        for (User user : users) {
            boolean isAdmin = user.getAuthorities().stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));
            boolean alreadyOwner = user.getAuthorities().stream().anyMatch(a -> AuthoritiesConstants.PLATFORM_OWNER.equals(a.getName()));
            if (isAdmin && !alreadyOwner) {
                user.getAuthorities().add(ownerRole);
                userRepository.save(user);
                granted++;
                LOG.warn("Backfill: granted {} to user '{}'", AuthoritiesConstants.PLATFORM_OWNER, user.getLogin());
            }
        }
        return granted;
    }

    /**
     * The role → permission mapping to seed for this tenant: the business catalog
     * ({@link DefaultRolePermissions}) for every tenant, plus the platform catalog
     * ({@link PlatformRolePermissions}) when this is the platform tenant.
     */
    private Map<String, Set<String>> effectiveRolePermissions(Tenant tenant) {
        Map<String, Set<String>> mapping = new LinkedHashMap<>(DefaultRolePermissions.rolePermissions());
        if (isPlatformTenant(tenant)) {
            mapping.putAll(PlatformRolePermissions.rolePermissions());
        }
        return mapping;
    }

    private boolean isPlatformTenant(Tenant tenant) {
        String platform = multiTenantProperties.getPlatformTenant();
        return platform != null && platform.equalsIgnoreCase(tenant.getSubdomain());
    }

    private Map<String, Authority> seedAuthorities(Set<String> roleNames) {
        Map<String, Authority> roles = new LinkedHashMap<>();
        for (String name : roleNames) {
            Authority role = authorityRepository
                .findById(name)
                .orElseGet(() -> {
                    Authority a = new Authority();
                    a.setName(name);
                    return authorityRepository.save(a);
                });
            roles.put(name, role);
        }
        LOG.debug("Seeded {} authorities", roles.size());
        return roles;
    }

    private void seedPermissions(Map<String, Set<String>> rolePermissions, Map<String, Authority> roles) {
        // Distinct permission names across all roles, created once.
        Set<String> allPermissions = new LinkedHashSet<>();
        rolePermissions.values().forEach(allPermissions::addAll);

        Map<String, Permission> permissions = new LinkedHashMap<>();
        for (String permName : allPermissions) {
            Permission permission = permissionRepository
                .findById(permName)
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setName(permName);
                    return permissionRepository.save(p);
                });
            permissions.put(permName, permission);
        }

        // Bind each role's permissions (owned side is Authority#permissions).
        rolePermissions.forEach((roleName, permNames) -> {
            Authority role = roles.get(roleName);
            if (role == null) {
                return;
            }
            boolean changed = false;
            for (String permName : permNames) {
                if (role.getPermissions().stream().noneMatch(p -> p.getName().equals(permName))) {
                    role.getPermissions().add(permissions.get(permName));
                    changed = true;
                }
            }
            if (changed) {
                authorityRepository.save(role);
            }
        });
        LOG.debug("Seeded {} permissions and bound them to {} roles", permissions.size(), roles.size());
    }

    private void seedAdminUser(Tenant tenant, TenantRegistrationDTO registration, Map<String, Authority> roles) {
        String login = registration.getAdminEmail();
        if (userRepository.findOneByLogin(login).isPresent()) {
            LOG.debug("Admin user '{}' already exists for tenant '{}'; skipping", login, tenant.getSubdomain());
            return;
        }
        User admin = new User();
        admin.setLogin(login);
        admin.setEmail(registration.getAdminEmail());
        admin.setFirstName(registration.getAdminFirstName());
        admin.setLastName(registration.getAdminLastName());
        admin.setPassword(passwordEncoder.encode(registration.getAdminPassword()));
        admin.setActivated(true);
        admin.setLangKey("en");
        admin.setCreatedBy("system");
        admin.setCreatedDate(Instant.now());
        Set<Authority> adminAuthorities = new LinkedHashSet<>(
            List.of(roles.get(AuthoritiesConstants.ADMIN), roles.get(AuthoritiesConstants.USER))
        );
        // The platform tenant's admin is also the SaaS platform owner.
        if (isPlatformTenant(tenant) && roles.containsKey(AuthoritiesConstants.PLATFORM_OWNER)) {
            adminAuthorities.add(roles.get(AuthoritiesConstants.PLATFORM_OWNER));
        }
        admin.setAuthorities(adminAuthorities);
        userRepository.save(admin);
        LOG.info("Created admin user '{}' for tenant '{}'", login, tenant.getSubdomain());
    }

    private void seedDefaultConfiguration(Tenant tenant) {
        seedDefaultPayrollCalendar(tenant);
        seedDefaultPayComponents();
        seedDefaultApprovalChains(tenant);
        // LeaveTypeRule requires Country rows seeded into the tenant DB before we can FK to
        // them. Deferred to its own seed pass once the country reference dataset is loaded
        // by Liquibase (see ROADMAP §3 / —.
    }

    /**
     * Seed two-step DIRECT_MANAGER → DEPARTMENT_HEAD chains for LEAVE_REQUEST,
     * EXPENSE_CLAIM, OVERTIME_REQUEST . Idempotent: skips any approval type
     * that already has at least one active step (tenant has already configured it).
     * Validates the result via {@link ApprovalChainValidator} so a future tweak that
     * introduces a sequence gap fails the provisioning loudly instead of stalling
     * the orchestrator at runtime.
     */
    private void seedDefaultApprovalChains(Tenant tenant) {
        for (ApprovalType type : DEFAULT_CHAIN_TYPES) {
            if (approvalChainConfigRepository.existsByApprovalTypeAndActiveTrue(type)) {
                LOG.debug("Approval chain for {} already present on tenant '{}'; skipping", type, tenant.getSubdomain());
                continue;
            }
            for (int i = 0; i < DEFAULT_CHAIN_STEPS.size(); i++) {
                ApprovalChainConfig step = new ApprovalChainConfig();
                step.setApprovalType(type);
                step.setSequenceOrder(i + 1);
                step.setApproverType(DEFAULT_CHAIN_STEPS.get(i));
                step.setActive(true);
                step.setDescription("Seeded default " + type.name() + " step " + (i + 1));
                approvalChainConfigRepository.save(stampSystem(step));
            }
            ApprovalChainValidator.validate(
                type,
                approvalChainConfigRepository.findByApprovalTypeAndActiveTrueOrderBySequenceOrderAsc(type)
            );
            LOG.info("Seeded default approval chain for {} on tenant '{}'", type, tenant.getSubdomain());
        }
    }

    private void seedDefaultPayrollCalendar(Tenant tenant) {
        String calendarName = "Default Monthly";
        if (payrollCalendarRepository.findAll().stream().anyMatch(c -> calendarName.equals(c.getName()))) {
            return;
        }
        PayrollCalendar calendar = new PayrollCalendar();
        calendar.setName(calendarName);
        calendar.setFrequency(Frequency.MONTHLY);
        calendar.setTimezone(tenant.getTimezone() != null ? tenant.getTimezone() : TimeZone.getTimeZone("UTC"));
        calendar.setActive(true);
        payrollCalendarRepository.save(stampSystem(calendar));
        LOG.debug("Seeded default payroll calendar for tenant '{}'", tenant.getSubdomain());
    }

    private void seedDefaultPayComponents() {
        seedPayComponent(PayComponentCode.BASIC, "Basic salary", Kind.EARNING, Measurement.AMOUNT, true, true);
        seedPayComponent(PayComponentCode.OT, "Overtime", Kind.EARNING, Measurement.HOURS, true, true);
        seedPayComponent(PayComponentCode.BONUS, "Bonus", Kind.EARNING, Measurement.AMOUNT, true, false);
        seedPayComponent(PayComponentCode.TAX_PIT, "Personal income tax", Kind.DEDUCTION, Measurement.AMOUNT, false, false);
    }

    private void seedPayComponent(PayComponentCode code, String name, Kind kind, Measurement measure, boolean taxable, boolean social) {
        if (payComponentRepository.findAll().stream().anyMatch(c -> c.getCode() == code)) {
            return;
        }
        PayComponent component = new PayComponent();
        component.setCode(code);
        component.setName(name);
        component.setKind(kind);
        component.setMeasure(measure);
        component.setTaxable(taxable);
        component.setContributesToSocial(social);
        payComponentRepository.save(stampSystem(component));
    }

    /**
     * Stamps {@code createdBy}/{@code createdDate} for rows seeded during tenant initialization. JPA
     * auditing does not auto-populate them in this provisioning path (no request/security context), so
     * seeded tenant rows must set them explicitly — same as {@code seedAdminUser} — or the NOT NULL
     * {@code created_by} column rejects the insert.
     */
    private static <E extends AbstractAuditingEntity<?>> E stampSystem(E entity) {
        entity.setCreatedBy("system");
        entity.setCreatedDate(Instant.now());
        return entity;
    }
}
