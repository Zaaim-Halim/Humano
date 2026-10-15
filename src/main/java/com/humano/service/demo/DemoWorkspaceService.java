package com.humano.service.demo;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.tenant.DemoSetupStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.shared.User;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.CreateSubscriptionRequest;
import com.humano.dto.demo.ConvertDemoRequest;
import com.humano.dto.demo.CreateDemoWorkspaceRequest;
import com.humano.dto.demo.DemoCredentials;
import com.humano.dto.demo.DemoWorkspaceCreated;
import com.humano.dto.demo.DemoWorkspaceResponse;
import com.humano.dto.demo.ExtendDemoRequest;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.billing.SubscriptionService;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.multitenancy.TenantProvisioningService;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo workspaces: tenants the platform team sets up for prospects, filled with a sample company.
 * <p>
 * A demo has no subscription, so it never reaches billing, MRR or trial processing, and it is left
 * out of the customer list and metrics. It runs until {@code demoExpiresAt}, when it is suspended
 * (the workspace locks, its data stays) until it is extended, converted to a customer or deleted.
 * <p>
 * Setup is slow (a database, its migrations and ~50 employees with payroll), so it runs in the
 * background: {@link Tenant#getDemoSetup()} tracks it, and a failed setup can be retried — both
 * provisioning and the sample company resume where they stopped. Every login of a demo (the admin
 * and one persona per role, see {@link DemoPersonaService}) shares one generated password, returned
 * once and never stored.
 */
@Service
public class DemoWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(DemoWorkspaceService.class);
    private static final String ENTITY_NAME = "demo";
    private static final int DEFAULT_DAYS = 14;
    private static final Set<TenantStatus> GONE = EnumSet.of(TenantStatus.DEACTIVATED, TenantStatus.DELETED);
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SecureRandom random = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final TenantProvisioningService provisioningService;
    private final SubscriptionService subscriptionService;
    private final DemoRequestService demoRequestService;
    private final DemoPersonaService personaService;
    private final ObjectProvider<DemoDataSeeder> demoDataSeeder;
    private final MultiTenantProperties multiTenantProperties;
    private final PasswordEncoder passwordEncoder;
    private final Executor executor;
    private final TransactionTemplate masterTx;
    private final TransactionTemplate tenantTx;

    public DemoWorkspaceService(
        TenantRepository tenantRepository,
        SubscriptionPlanRepository planRepository,
        UserRepository userRepository,
        TenantProvisioningService provisioningService,
        SubscriptionService subscriptionService,
        DemoRequestService demoRequestService,
        DemoPersonaService personaService,
        ObjectProvider<DemoDataSeeder> demoDataSeeder,
        MultiTenantProperties multiTenantProperties,
        PasswordEncoder passwordEncoder,
        @Qualifier("taskExecutor") Executor executor,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
        this.subscriptionService = subscriptionService;
        this.demoRequestService = demoRequestService;
        this.personaService = personaService;
        this.demoDataSeeder = demoDataSeeder;
        this.multiTenantProperties = multiTenantProperties;
        this.passwordEncoder = passwordEncoder;
        this.executor = executor;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    /** Every demo workspace that still exists, newest first. */
    public List<DemoWorkspaceResponse> list() {
        return masterTx.execute(tx ->
            tenantRepository
                .findByDemoTrueOrderByCreatedDateDesc()
                .stream()
                .filter(t -> !GONE.contains(t.getStatus()))
                .map(DemoWorkspaceService::toResponse)
                .toList()
        );
    }

    /** Records the demo and starts its setup in the background; returns its sign-in details. */
    public DemoWorkspaceCreated create(CreateDemoWorkspaceRequest request) {
        String subdomain = request.subdomain().toLowerCase();
        if (multiTenantProperties.isReservedSubdomain(subdomain)) {
            throw new BadRequestAlertException("This subdomain is reserved", ENTITY_NAME, "subdomainreserved");
        }
        String domain = subdomain + ".demo";
        boolean sampleData = !Boolean.FALSE.equals(request.sampleData());
        Tenant tenant = masterTx.execute(tx -> {
            if (tenantRepository.existsBySubdomain(subdomain) || tenantRepository.existsByDomain(domain)) {
                throw new BadRequestAlertException("This subdomain is already taken", ENTITY_NAME, "subdomaintaken");
            }
            Tenant t = new Tenant();
            t.setName(request.companyName().trim());
            t.setSubdomain(subdomain);
            t.setDomain(domain);
            t.setCountry(request.country());
            t.setTimezone(TimeZone.getTimeZone(request.timezone() != null && !request.timezone().isBlank() ? request.timezone() : "UTC"));
            t.setSubscriptionPlan(showcasePlan(request.planId()));
            t.setStatus(TenantStatus.PENDING_SETUP);
            t.setDemo(true);
            int days = request.durationDays() != null ? request.durationDays() : DEFAULT_DAYS;
            t.setDemoExpiresAt(Instant.now().plus(days, ChronoUnit.DAYS));
            t.setDemoContactName(blankToNull(request.contactName()));
            t.setDemoContactEmail(blankToNull(request.contactEmail()));
            t.setDemoSetup(DemoSetupStatus.PROVISIONING);
            t = tenantRepository.save(t);
            if (request.demoRequestId() != null) {
                demoRequestService.markDemoCreated(request.demoRequestId(), t);
            }
            return t;
        });
        log.info("Demo workspace '{}' recorded; setting it up in the background", subdomain);
        String password = generatePassword();
        startSetup(tenant, password, sampleData);
        return new DemoWorkspaceCreated(toResponse(tenant), credentials(subdomain, password));
    }

    /** Retries a failed setup from where it stopped, with a new password. */
    public DemoWorkspaceCreated retrySetup(UUID id) {
        Tenant tenant = masterTx.execute(tx -> {
            Tenant t = findDemo(id);
            if (t.getDemoSetup() != DemoSetupStatus.FAILED) {
                throw new BadRequestAlertException("Only a failed setup can be retried", ENTITY_NAME, "notfailed");
            }
            t.setDemoSetup(DemoSetupStatus.PROVISIONING);
            return tenantRepository.save(t);
        });
        String password = generatePassword();
        startSetup(tenant, password, true);
        return new DemoWorkspaceCreated(masterTx.execute(tx -> toResponse(findDemo(id))), credentials(tenant.getSubdomain(), password));
    }

    /** A new shared password for every login of a ready demo (the old one is lost or was shared). */
    public DemoCredentials resetPassword(UUID id) {
        Tenant tenant = masterTx.execute(tx -> findDemo(id));
        if (tenant.getDemoSetup() != DemoSetupStatus.READY) {
            throw new BadRequestAlertException("The demo is not ready yet", ENTITY_NAME, "notready");
        }
        String password = generatePassword();
        String encoded = passwordEncoder.encode(password);
        inTenant(tenant.getSubdomain(), () ->
            tenantTx.executeWithoutResult(tx -> {
                for (User user : userRepository.findAll()) {
                    if (user.getLogin().endsWith("@" + DemoPersonaService.EMAIL_DOMAIN)) {
                        user.setPassword(encoded);
                        userRepository.save(user);
                    }
                }
            })
        );
        log.info("Reset the demo password of '{}'", tenant.getSubdomain());
        return credentials(tenant.getSubdomain(), password);
    }

    /** More time; reopens a demo that expired. */
    public DemoWorkspaceResponse extend(UUID id, ExtendDemoRequest request) {
        Tenant tenant = masterTx.execute(tx -> {
            Tenant t = findDemo(id);
            Instant now = Instant.now();
            Instant from = t.getDemoExpiresAt() != null && t.getDemoExpiresAt().isAfter(now) ? t.getDemoExpiresAt() : now;
            t.setDemoExpiresAt(from.plus(request.days(), ChronoUnit.DAYS));
            return tenantRepository.save(t);
        });
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            provisioningService.activateTenant(id);
        }
        return masterTx.execute(tx -> toResponse(findDemo(id)));
    }

    /** Ends the demo now: the workspace locks, its data stays. */
    public DemoWorkspaceResponse expire(UUID id) {
        Tenant tenant = masterTx.execute(tx -> {
            Tenant t = findDemo(id);
            t.setDemoExpiresAt(Instant.now());
            return tenantRepository.save(t);
        });
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            provisioningService.suspendTenant(id);
        }
        return masterTx.execute(tx -> toResponse(findDemo(id)));
    }

    /**
     * Makes the demo a customer: it keeps its workspace and data, leaves the demo list, reopens if
     * it had expired, and gets a subscription (with a trial when {@code trialDays} is set).
     */
    public void convert(UUID id, ConvertDemoRequest request) {
        Tenant demo = masterTx.execute(tx -> {
            Tenant t = findDemo(id);
            if (t.getDemoSetup() != DemoSetupStatus.READY) {
                throw new BadRequestAlertException("The demo is not ready yet", ENTITY_NAME, "notready");
            }
            planRepository
                .findById(request.planId())
                .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", request.planId()));
            return t;
        });
        Instant trialEnd = request.trialDays() != null && request.trialDays() > 0
            ? Instant.now().plus(request.trialDays(), ChronoUnit.DAYS)
            : null;
        // The subscription first: it validates the coupon, and if it fails the workspace is still a demo.
        subscriptionService.createSubscription(
            new CreateSubscriptionRequest(id, request.planId(), request.billingCycle(), true, trialEnd, blankToNull(request.couponCode()))
        );
        masterTx.executeWithoutResult(tx -> {
            Tenant t = findDemo(id);
            t.setSubscriptionPlan(planRepository.getReferenceById(request.planId()));
            t.setDemo(false);
            t.setDemoExpiresAt(null);
            t.setDemoSetup(null);
            tenantRepository.save(t);
        });
        if (demo.getStatus() == TenantStatus.SUSPENDED) {
            provisioningService.activateTenant(id);
        }
        log.info("Demo '{}' converted to a customer", demo.getSubdomain());
    }

    /** Deletes the demo's database; the workspace is gone for good. */
    public void delete(UUID id) {
        masterTx.execute(tx -> findDemo(id));
        provisioningService.deprovisionTenant(id);
    }

    /** Hourly: suspends demos past their expiry. */
    @Scheduled(cron = "0 10 * * * *")
    public void suspendExpiredDemos() {
        List<Tenant> expired = masterTx.execute(tx -> tenantRepository.findExpiredDemos(Instant.now()));
        if (expired == null) {
            return;
        }
        for (Tenant t : expired) {
            try {
                provisioningService.suspendTenant(t.getId());
                log.info("Demo '{}' expired; workspace suspended", t.getSubdomain());
            } catch (Exception e) {
                log.error("Could not suspend expired demo '{}'", t.getSubdomain(), e);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private void startSetup(Tenant tenant, String password, boolean sampleData) {
        UUID id = tenant.getId();
        String subdomain = tenant.getSubdomain();
        TenantRegistrationDTO registration = registration(tenant, password);
        executor.execute(() -> {
            try {
                provisioningService.provisionTenant(registration);
                setSetup(id, DemoSetupStatus.SEEDING);
                inTenant(subdomain, () -> {
                    personaService.createPersonas(password);
                    if (sampleData) {
                        demoDataSeeder.getObject().seed();
                    }
                });
                setSetup(id, DemoSetupStatus.READY);
                log.info("Demo workspace '{}' is ready", subdomain);
            } catch (Exception e) {
                log.error("Demo workspace '{}' setup failed", subdomain, e);
                try {
                    setSetup(id, DemoSetupStatus.FAILED);
                } catch (Exception inner) {
                    log.error("Also failed to record the failed setup of '{}'", subdomain, inner);
                }
            }
        });
    }

    private TenantRegistrationDTO registration(Tenant tenant, String password) {
        TenantRegistrationDTO dto = new TenantRegistrationDTO();
        dto.setCompanyName(tenant.getName());
        dto.setSubdomain(tenant.getSubdomain());
        dto.setDomain(tenant.getDomain());
        dto.setTimezone(tenant.getTimezone().getID());
        dto.setAdminEmail(DemoPersonaService.emailForRole(AuthoritiesConstants.ADMIN));
        dto.setAdminFirstName("Demo");
        dto.setAdminLastName("Admin");
        dto.setAdminPassword(password);
        return dto;
    }

    private void setSetup(UUID id, DemoSetupStatus status) {
        masterTx.executeWithoutResult(tx -> {
            Tenant t = findDemo(id);
            t.setDemoSetup(status);
            tenantRepository.save(t);
        });
    }

    /** The plan whose features the demo shows: the one asked for, else the featured plan, else the dearest active one. */
    private SubscriptionPlan showcasePlan(UUID planId) {
        if (planId != null) {
            return planRepository.findById(planId).orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", planId));
        }
        List<SubscriptionPlan> active = planRepository.findByActiveTrue();
        return active
            .stream()
            .filter(SubscriptionPlan::isFeatured)
            .findFirst()
            .or(() -> active.stream().max(Comparator.comparing(SubscriptionPlan::getPrice)))
            .orElseThrow(() -> new BadRequestAlertException("Create a plan first", ENTITY_NAME, "noplan"));
    }

    private Tenant findDemo(UUID id) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("Tenant", id));
        if (!t.isDemo()) {
            throw new BadRequestAlertException("This workspace is not a demo", ENTITY_NAME, "notademo");
        }
        return t;
    }

    private DemoCredentials credentials(String subdomain, String password) {
        String admin = DemoPersonaService.emailForRole(AuthoritiesConstants.ADMIN);
        List<String> personas = List.of(
            DemoPersonaService.emailForRole(AuthoritiesConstants.HR_MANAGER),
            DemoPersonaService.emailForRole(AuthoritiesConstants.PAYROLL_ADMIN),
            DemoPersonaService.emailForRole(AuthoritiesConstants.MANAGER),
            DemoPersonaService.emailForRole(AuthoritiesConstants.EMPLOYEE)
        );
        return new DemoCredentials(subdomain, admin, personas, password);
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder("Demo-");
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.append(random.nextInt(10)).toString();
    }

    private static void inTenant(String subdomain, Runnable body) {
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(subdomain);
        try {
            body.run();
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private static DemoWorkspaceResponse toResponse(Tenant t) {
        SubscriptionPlan plan = t.getSubscriptionPlan();
        return new DemoWorkspaceResponse(
            t.getId(),
            t.getName(),
            t.getSubdomain(),
            t.getCountry(),
            t.getStatus(),
            t.getDemoSetup(),
            t.getDemoExpiresAt(),
            t.getDemoContactName(),
            t.getDemoContactEmail(),
            plan == null ? null : plan.getId(),
            plan == null ? null : (plan.getDisplayName() != null ? plan.getDisplayName() : plan.getSubscriptionType().name()),
            t.getCreatedDate()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
