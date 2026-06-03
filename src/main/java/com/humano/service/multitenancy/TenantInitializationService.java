package com.humano.service.multitenancy;

import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Permission;
import com.humano.domain.shared.User;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.PermissionRepository;
import com.humano.repository.shared.UserRepository;
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
 * payroll reference data (P1.5).
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

    /** Roles seeded for every new tenant. ROLE_USER stays so JHipster's baseline keeps working. */
    public static final List<String> DEFAULT_ROLES = List.of("ROLE_ADMIN", "ROLE_USER", "ROLE_HR", "ROLE_MANAGER", "ROLE_EMPLOYEE");

    /**
     * Baseline permission codes + which roles get them. Keep the alphabet small — finer-grained
     * permissions can come later via {@code PermissionsConstants} (P6.1). Order is preserved by
     * {@link LinkedHashMap} so the bind step is deterministic.
     */
    private static final Map<String, Set<String>> DEFAULT_PERMISSIONS = new LinkedHashMap<>();

    static {
        DEFAULT_PERMISSIONS.put("EMPLOYEE_READ", Set.of("ROLE_ADMIN", "ROLE_HR", "ROLE_MANAGER", "ROLE_EMPLOYEE"));
        DEFAULT_PERMISSIONS.put("EMPLOYEE_WRITE", Set.of("ROLE_ADMIN", "ROLE_HR"));
        DEFAULT_PERMISSIONS.put("PAYROLL_RUN", Set.of("ROLE_ADMIN", "ROLE_HR"));
        DEFAULT_PERMISSIONS.put("BILLING_VIEW", Set.of("ROLE_ADMIN"));
    }

    private final AuthorityRepository authorityRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PayrollCalendarRepository payrollCalendarRepository;
    private final PayComponentRepository payComponentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tenantTx;

    public TenantInitializationService(
        AuthorityRepository authorityRepository,
        PermissionRepository permissionRepository,
        UserRepository userRepository,
        PayrollCalendarRepository payrollCalendarRepository,
        PayComponentRepository payComponentRepository,
        PasswordEncoder passwordEncoder,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.authorityRepository = authorityRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.payrollCalendarRepository = payrollCalendarRepository;
        this.payComponentRepository = payComponentRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    /**
     * Seed the tenant DB. Idempotent: existing roles/permissions/users/calendars/components
     * are left in place, so it's safe to re-run on a partially-provisioned tenant (P1.6).
     */
    public void initializeTenant(Tenant tenant, TenantRegistrationDTO registration) {
        LOG.info("Initializing tenant '{}' with default data", tenant.getSubdomain());

        String previousTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(tenant.getSubdomain());
        try {
            tenantTx.executeWithoutResult(status -> {
                Map<String, Authority> roles = seedAuthorities();
                seedPermissions(roles);
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

    private Map<String, Authority> seedAuthorities() {
        Map<String, Authority> roles = new LinkedHashMap<>();
        for (String name : DEFAULT_ROLES) {
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

    private void seedPermissions(Map<String, Authority> roles) {
        DEFAULT_PERMISSIONS.forEach((permName, owningRoles) -> {
            Permission permission = permissionRepository
                .findById(permName)
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setName(permName);
                    return permissionRepository.save(p);
                });
            // Bind permission to its roles (owned side is Authority#permissions)
            for (String roleName : owningRoles) {
                Authority role = roles.get(roleName);
                if (role != null && role.getPermissions().stream().noneMatch(p -> p.getName().equals(permName))) {
                    role.getPermissions().add(permission);
                    authorityRepository.save(role);
                }
            }
        });
        LOG.debug("Seeded {} permissions and bound them to roles", DEFAULT_PERMISSIONS.size());
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
        admin.setAuthorities(new LinkedHashSet<>(List.of(roles.get("ROLE_ADMIN"), roles.get("ROLE_USER"))));
        userRepository.save(admin);
        LOG.info("Created admin user '{}' for tenant '{}'", login, tenant.getSubdomain());
    }

    private void seedDefaultConfiguration(Tenant tenant) {
        seedDefaultPayrollCalendar(tenant);
        seedDefaultPayComponents();
        // LeaveTypeRule requires Country rows seeded into the tenant DB before we can FK to
        // them. Deferred to its own seed pass once the country reference dataset is loaded
        // by Liquibase (see ROADMAP §3 / P3.3).
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
        payrollCalendarRepository.save(calendar);
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
        payComponentRepository.save(component);
    }
}
