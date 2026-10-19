package com.humano.dev;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.demo.DemoDataSeeder;
import com.humano.service.demo.DemoPersonaService;
import com.humano.service.multitenancy.TenantInitializationService;
import com.humano.service.multitenancy.TenantProvisioningService;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds a ready-to-use demo tenant with one login per role, for local testing only.
 * <p>
 * Active only under the {@code dev} profile and when {@code humano.dev.seed.enabled} is not
 * {@code false}. It is fully <strong>fail-soft</strong>: any error (MySQL down, partial provisioning,
 * a bad user) is logged and swallowed so it can never block application startup. It is
 * <strong>idempotent</strong>: an already-ACTIVE demo tenant is reused, and existing users are skipped.
 *
 * <h3>What it creates</h3>
 * A business tenant on subdomain {@value #SUBDOMAIN} (deliberately NOT the platform {@code default}
 * tenant, whose boot-time backfill would auto-escalate every admin to PLATFORM_OWNER), then one
 * activated user per role present in that tenant's authority catalog — each holding that role plus
 * {@code ROLE_USER}, all sharing password {@value #PASSWORD}.
 *
 * <h3>How to log in</h3>
 * Auth resolves the user inside the tenant context, so requests must target the demo tenant via the
 * {@code X-Tenant-ID: }{@value #SUBDOMAIN} header (or its subdomain). The exact logins are printed to
 * the log on startup; see {@code src/main/java/com/humano/dev/CLAUDE.md}.
 *
 * <h3>Requirements</h3>
 * Provisioning creates a physical MySQL database, so a dev MySQL server must be reachable. With H2 only,
 * provisioning fails (logged, non-fatal) and no demo tenant is created.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "humano.dev.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Demo business tenant subdomain — also the value to send as {@code X-Tenant-ID}. */
    static final String SUBDOMAIN = "demo";

    /** Shared password for every seeded demo login. Dev only. */
    static final String PASSWORD = "Passw0rd!";
    /** The platform (SaaS owner) console login, in the platform tenant. */
    static final String OWNER_LOGIN = "owner@humano.app";

    private static final String EMAIL_DOMAIN = DemoPersonaService.EMAIL_DOMAIN;

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService provisioningService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AuthorityRepository authorityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate masterTx;
    private final TransactionTemplate tenantTx;
    private final ObjectProvider<DemoDataSeeder> demoDataSeeder;
    private final DemoPersonaService personaService;
    private final boolean seedDemoData;
    private final TenantInitializationService initializationService;
    private final PlatformDemoSeeder platformDemoSeeder;
    private final MultiTenantProperties multiTenantProperties;

    public DevDataSeeder(
        TenantRepository tenantRepository,
        TenantProvisioningService provisioningService,
        SubscriptionPlanRepository subscriptionPlanRepository,
        AuthorityRepository authorityRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager,
        ObjectProvider<DemoDataSeeder> demoDataSeeder,
        DemoPersonaService personaService,
        TenantInitializationService initializationService,
        MultiTenantProperties multiTenantProperties,
        PlatformDemoSeeder platformDemoSeeder,
        @Value("${humano.dev.seed.demo-data:true}") boolean seedDemoData
    ) {
        this.demoDataSeeder = demoDataSeeder;
        this.personaService = personaService;
        this.initializationService = initializationService;
        this.platformDemoSeeder = platformDemoSeeder;
        this.multiTenantProperties = multiTenantProperties;
        this.seedDemoData = seedDemoData;
        this.tenantRepository = tenantRepository;
        this.provisioningService = provisioningService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.authorityRepository = authorityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedPlatformOwner();
        } catch (Exception e) {
            log.error("Platform owner seeding failed (non-fatal): {}", e.getMessage(), e);
        }
        try {
            Tenant tenant = ensureDemoTenant();
            if (tenant == null) {
                return; // provisioning failed; already logged
            }
            seedUsers(tenant);
            if (seedDemoData) {
                seedDemoCompany(tenant);
                try {
                    platformDemoSeeder.seed(SUBDOMAIN);
                } catch (Exception e) {
                    log.error("Platform demo seeding failed (non-fatal): {}", e.getMessage(), e);
                }
            }
            logCredentials();
        } catch (Exception e) {
            // Never let dev seeding break startup.
            log.error("Dev data seeding failed (non-fatal, app continues): {}", e.getMessage(), e);
        }
    }

    /** Provisions the demo tenant if it isn't already ACTIVE. Returns null on failure. */
    private Tenant ensureDemoTenant() {
        try {
            Optional<Tenant> existing = tenantRepository.findBySubdomain(SUBDOMAIN);
            if (existing.isPresent() && existing.orElseThrow().getStatus() == TenantStatus.ACTIVE) {
                log.info("[dev-seed] demo tenant '{}' already ACTIVE; reusing it", SUBDOMAIN);
                return existing.orElseThrow();
            }
            // provisionTenant's own createTenantRecord does NOT set the @NotNull country / subscription
            // plan (real onboarding pre-creates that row), so seed a valid PENDING_SETUP row first and
            // let provisionTenant resume it.
            masterTx.executeWithoutResult(tx -> {
                if (tenantRepository.findBySubdomain(SUBDOMAIN).isEmpty()) {
                    tenantRepository.save(buildDemoTenant());
                    log.info("[dev-seed] created demo tenant master row '{}'", SUBDOMAIN);
                }
            });
            log.info("[dev-seed] provisioning demo tenant '{}' (needs a reachable dev MySQL)…", SUBDOMAIN);
            return provisioningService.provisionTenant(demoRegistration());
        } catch (Exception e) {
            log.error("[dev-seed] could not provision demo tenant '{}' (non-fatal): {}", SUBDOMAIN, e.getMessage(), e);
            return null;
        }
    }

    /** A valid {@code PENDING_SETUP} tenant row with every {@code @NotNull} field set. */
    private Tenant buildDemoTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Demo Company");
        tenant.setDomain("demo-company.com"); // single label.tld — matches the Tenant.domain pattern
        tenant.setSubdomain(SUBDOMAIN);
        tenant.setTimezone(TimeZone.getTimeZone("UTC"));
        tenant.setCountry(CountryCode.US);
        tenant.setSubscriptionPlan(resolveOrCreatePlan());
        tenant.setStatus(TenantStatus.PENDING_SETUP);
        return tenant;
    }

    /** Reuse a seeded plan if one exists; otherwise create a minimal free dev plan (in the master tx). */
    private SubscriptionPlan resolveOrCreatePlan() {
        return subscriptionPlanRepository
            .findByActiveTrue()
            .stream()
            .findFirst()
            .or(() -> subscriptionPlanRepository.findAll().stream().findFirst())
            .orElseGet(() -> {
                SubscriptionPlan plan = new SubscriptionPlan();
                plan.setSubscriptionType(SubscriptionType.FREE);
                plan.setPrice(BigDecimal.ZERO);
                plan.setDisplayName("Dev Free");
                plan.setPublicListed(false); // internal dev plan, not on the public pricing page
                plan.setActive(true);
                return subscriptionPlanRepository.save(plan);
            });
    }

    private TenantRegistrationDTO demoRegistration() {
        TenantRegistrationDTO dto = new TenantRegistrationDTO();
        dto.setCompanyName("Demo Company");
        dto.setSubdomain(SUBDOMAIN);
        dto.setDomain("demo-company.com");
        dto.setTimezone("UTC");
        dto.setAdminEmail(emailForRole(AuthoritiesConstants.ADMIN));
        dto.setAdminFirstName("Demo");
        dto.setAdminLastName("Admin");
        dto.setAdminPassword(PASSWORD);
        // preferredRegion left null → non-dedicated, round-robin to the default (localhost) dev DB server.
        return dto;
    }

    /** Fills the demo tenant with a realistic company (see {@link DemoDataSeeder}). */
    private void seedDemoCompany(Tenant tenant) {
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(tenant.getSubdomain());
        try {
            demoDataSeeder.getObject().seed();
        } catch (Exception e) {
            log.error("[demo-data] seeding failed (non-fatal, app continues): {}", e.getMessage(), e);
        } finally {
            restoreTenant(previous);
        }
    }

    /** Creates one activated persona login per role (see {@link DemoPersonaService}). */
    private void seedUsers(Tenant tenant) {
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(tenant.getSubdomain());
        try {
            personaService.createPersonas(PASSWORD);
        } finally {
            restoreTenant(previous);
        }
    }

    /**
     * The SaaS owner's console login ({@value #OWNER_LOGIN}, organization = the platform tenant):
     * seeds the platform role catalog, then one {@code ROLE_PLATFORM_OWNER} user. Idempotent.
     */
    private void seedPlatformOwner() {
        initializationService.backfillPlatformTenant();
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(multiTenantProperties.getPlatformTenant());
        try {
            tenantTx.executeWithoutResult(status -> {
                if (userRepository.findOneByLogin(OWNER_LOGIN).isPresent()) {
                    return;
                }
                User owner = new User();
                owner.setLogin(OWNER_LOGIN);
                owner.setEmail(OWNER_LOGIN);
                owner.setFirstName("Platform");
                owner.setLastName("Owner");
                owner.setPassword(passwordEncoder.encode(PASSWORD));
                owner.setActivated(true);
                owner.setLangKey("en");
                Set<Authority> grants = new LinkedHashSet<>();
                authorityRepository.findById(AuthoritiesConstants.PLATFORM_OWNER).ifPresent(grants::add);
                authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(grants::add);
                owner.setAuthorities(grants);
                userRepository.save(owner);
                log.info(
                    "[dev-seed] created platform owner {} (organization '{}')",
                    OWNER_LOGIN,
                    multiTenantProperties.getPlatformTenant()
                );
            });
        } finally {
            restoreTenant(previous);
        }
    }

    private void logCredentials() {
        log.info(
            "\n========================= DEV DEMO LOGINS =========================\n" +
            "  Tenant   : {}   (send header  X-Tenant-ID: {})\n" +
            "  Password : {}   (same for every login below)\n" +
            "  Logins   : <role>@{}  e.g. admin@{}, hr.manager@{}, payroll.admin@{}\n" +
            "  (one login per role; see the [dev-seed] 'created …' lines above for the full list)\n" +
            "  Platform : owner@humano.app  (organization: {}) — the SaaS owner console\n" +
            "==================================================================",
            SUBDOMAIN,
            SUBDOMAIN,
            PASSWORD,
            EMAIL_DOMAIN,
            EMAIL_DOMAIN,
            EMAIL_DOMAIN,
            EMAIL_DOMAIN,
            multiTenantProperties.getPlatformTenant()
        );
    }

    private void restoreTenant(String previous) {
        if (previous != null) {
            TenantContext.setCurrentTenant(previous);
        } else {
            TenantContext.clear();
        }
    }

    /** {@code ROLE_HR_MANAGER} → {@code hr.manager@demo.humano}. */
    static String emailForRole(String role) {
        return DemoPersonaService.emailForRole(role);
    }
}
