package com.humano.service.signup;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.payroll.OrganizationSettings;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.CreateSubscriptionRequest;
import com.humano.dto.signup.CompleteSignupRequest;
import com.humano.dto.signup.SignupView;
import com.humano.dto.signup.StartSignupRequest;
import com.humano.dto.signup.SubdomainCheck;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.payroll.OrganizationSettingsRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.BillingMailService;
import com.humano.service.billing.SubscriptionService;
import com.humano.service.multitenancy.TenantProvisioningService;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

/**
 * Self-serve sign-up from the public site — a free trial, no card.
 * <ol>
 *   <li>{@link #start}: the visitor picks a plan, a company name and a workspace address. Nothing is
 *       stored; a signed link carrying those details is emailed to them ({@link SignupLink}).</li>
 *   <li>{@link #complete}: opening the link confirms the email; the person chooses their name and
 *       password, and the workspace is recorded (a {@link Tenant}, whose demo contact fields hold who
 *       signed up) and set up in the background, like a demo workspace.</li>
 *   <li>Once it is ready the trial starts (a {@code TRIAL} subscription on the chosen plan) and the
 *       welcome email goes out. The same link follows the setup ({@link #view}) and retries a failed
 *       one — provisioning resumes where it stopped.</li>
 * </ol>
 * One trial per email: signing up again with an address that already has a workspace emails a
 * reminder of that workspace instead.
 */
@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);
    private static final String ENTITY_NAME = "signup";

    public static final int TRIAL_DAYS = 14;
    private static final int LINK_VALID_HOURS = 48;
    private static final Pattern SUBDOMAIN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Set<TenantStatus> GONE = EnumSet.of(TenantStatus.DEACTIVATED, TenantStatus.DELETED);
    private static final Set<TenantStatus> SETTING_UP = EnumSet.of(TenantStatus.PENDING_SETUP, TenantStatus.PROVISIONING);

    /** Throwaway-inbox providers: a trial needs an address someone keeps. */
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
        "mailinator.com",
        "guerrillamail.com",
        "guerrillamail.net",
        "sharklasers.com",
        "10minutemail.com",
        "temp-mail.org",
        "tempmail.com",
        "tempmailo.com",
        "yopmail.com",
        "trashmail.com",
        "getnada.com",
        "dispostable.com",
        "maildrop.cc",
        "throwawaymail.com",
        "fakeinbox.com",
        "mohmal.com",
        "emailondeck.com",
        "mintemail.com"
    );

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final TenantProvisioningService provisioningService;
    private final OrganizationSettingsRepository settingsRepository;
    private final MultiTenantProperties multiTenantProperties;
    private final BillingMailService mailService;
    private final SignupLink signupLink;
    private final JHipsterProperties jHipsterProperties;
    private final Environment environment;
    private final Executor executor;
    private final TransactionTemplate masterTx;
    private final TransactionTemplate tenantTx;

    /** Workspaces whose setup is running in this instance right now. */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public SignupService(
        TenantRepository tenantRepository,
        SubscriptionPlanRepository planRepository,
        SubscriptionRepository subscriptionRepository,
        SubscriptionService subscriptionService,
        TenantProvisioningService provisioningService,
        OrganizationSettingsRepository settingsRepository,
        MultiTenantProperties multiTenantProperties,
        BillingMailService mailService,
        SignupLink signupLink,
        JHipsterProperties jHipsterProperties,
        Environment environment,
        @Qualifier("taskExecutor") Executor executor,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.provisioningService = provisioningService;
        this.settingsRepository = settingsRepository;
        this.multiTenantProperties = multiTenantProperties;
        this.mailService = mailService;
        this.signupLink = signupLink;
        this.jHipsterProperties = jHipsterProperties;
        this.environment = environment;
        this.executor = executor;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    /** Whether a workspace address can be used (the form checks it as the visitor types). */
    public SubdomainCheck checkSubdomain(String raw) {
        String subdomain = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (subdomain.length() < 3 || subdomain.length() > 25 || !SUBDOMAIN.matcher(subdomain).matches()) {
            return new SubdomainCheck(subdomain, false, "invalid");
        }
        if (multiTenantProperties.isReservedSubdomain(subdomain)) {
            return new SubdomainCheck(subdomain, false, "reserved");
        }
        boolean taken = Boolean.TRUE.equals(
            masterTx.execute(tx -> tenantRepository.existsBySubdomain(subdomain) || tenantRepository.existsByDomain(domainFor(subdomain)))
        );
        return new SubdomainCheck(subdomain, !taken, taken ? "taken" : null);
    }

    /**
     * Emails the confirmation link. A filled-in honeypot is accepted without doing anything; an
     * address that already has a workspace gets a reminder of it instead of a second trial.
     */
    public void start(StartSignupRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Ignored a sign-up with the honeypot filled in");
            return;
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (DISPOSABLE_DOMAINS.contains(email.substring(email.indexOf('@') + 1))) {
            throw new BadRequestAlertException("Please use your work email", ENTITY_NAME, "disposableemail");
        }
        SubdomainCheck check = checkSubdomain(request.subdomain());
        if (!check.available()) {
            throw new BadRequestAlertException("This workspace address cannot be used", ENTITY_NAME, "subdomain" + check.reason());
        }
        SubscriptionPlan plan = masterTx.execute(tx -> planOnSale(request.planId()));

        Optional<Tenant> existing = masterTx.execute(tx -> ownWorkspace(email));
        if (existing != null && existing.isPresent()) {
            Tenant t = existing.orElseThrow();
            log.info("Sign-up for an address that already has workspace '{}'; reminding it", t.getSubdomain());
            mailService.sendSignupExistingWorkspace(email, t.getName(), t.getSubdomain());
            return;
        }

        String token = signupLink.issue(
            new SignupLink.Payload(
                email,
                request.companyName().trim(),
                check.subdomain(),
                request.country(),
                validZone(request.timezone()),
                plan.getId(),
                Instant.now().plus(LINK_VALID_HOURS, ChronoUnit.HOURS)
            )
        );
        String url = jHipsterProperties.getMail().getBaseUrl() + "/signup/verify?token=" + token;
        mailService.sendSignupConfirmation(email, request.companyName().trim(), check.subdomain(), url);
        if (environment.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            log.info("Sign-up confirmation link for {} (dev only): {}", email, url);
        }
        log.info("Sign-up started for workspace '{}'", check.subdomain());
    }

    /** What the emailed link shows: the sign-up, and how its workspace setup is going. */
    public SignupView view(String token) {
        SignupLink.Payload p = signupLink.read(token);
        Tenant tenant = masterTx.execute(tx -> ownTenant(p).orElse(null));
        if (tenant == null && p.expired()) {
            throw new BadRequestAlertException("This link has expired", ENTITY_NAME, "linkexpired");
        }
        return toView(p, tenant);
    }

    /**
     * Confirms the email and sets the workspace up in the background (or retries a failed setup —
     * provisioning resumes, the new password applies if the admin was not created yet).
     */
    public SignupView complete(CompleteSignupRequest request) {
        SignupLink.Payload p = signupLink.read(request.token());
        Tenant tenant = masterTx.execute(tx -> ownTenant(p).orElse(null));
        if (tenant == null) {
            if (p.expired()) {
                throw new BadRequestAlertException("This link has expired", ENTITY_NAME, "linkexpired");
            }
            tenant = record(p);
        }
        SignupView.Stage stage = stage(tenant);
        if (stage == SignupView.Stage.FAILED || (stage == SignupView.Stage.SETTING_UP && !running.contains(tenant.getId()))) {
            startSetup(tenant, p, request);
            tenant = masterTx.execute(tx -> ownTenant(p).orElse(null));
        }
        return toView(p, tenant);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Records the workspace (not set up yet) for the confirmed sign-up. The unique subdomain decides
     * a race: a double click finds its own row, anyone else finds the address taken.
     */
    private Tenant record(SignupLink.Payload p) {
        if (multiTenantProperties.isReservedSubdomain(p.subdomain())) {
            throw new BadRequestAlertException("This workspace address cannot be used", ENTITY_NAME, "subdomainreserved");
        }
        try {
            return masterTx.execute(tx -> {
                SubscriptionPlan plan = planOnSale(p.planId());
                Tenant t = new Tenant();
                t.setName(p.companyName());
                t.setSubdomain(p.subdomain());
                t.setDomain(companyDomain(p.email(), p.subdomain()));
                t.setCountry(p.country());
                t.setTimezone(TimeZone.getTimeZone(p.timezone()));
                t.setSubscriptionPlan(plan);
                t.setStatus(TenantStatus.PENDING_SETUP);
                t.setDemo(false);
                t.setDemoContactEmail(p.email());
                return tenantRepository.save(t);
            });
        } catch (DataIntegrityViolationException e) {
            Optional<Tenant> own = masterTx.execute(tx -> ownTenant(p));
            if (own != null && own.isPresent()) {
                return own.orElseThrow();
            }
            throw new BadRequestAlertException("This workspace address was taken in the meantime", ENTITY_NAME, "subdomaintaken");
        }
    }

    private void startSetup(Tenant tenant, SignupLink.Payload p, CompleteSignupRequest request) {
        UUID id = tenant.getId();
        if (!running.add(id)) {
            return;
        }
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        masterTx.executeWithoutResult(tx ->
            tenantRepository
                .findById(id)
                .ifPresent(t -> {
                    t.setDemoContactName((firstName + " " + lastName).trim());
                    tenantRepository.save(t);
                })
        );
        TenantRegistrationDTO registration = new TenantRegistrationDTO();
        registration.setCompanyName(tenant.getName());
        registration.setSubdomain(tenant.getSubdomain());
        registration.setDomain(tenant.getDomain());
        registration.setTimezone(tenant.getTimezone().getID());
        // Loaded here: the tenant's plan is a lazy proxy outside its transaction, and server selection reads it.
        registration.setSubscriptionPlan(masterTx.execute(tx -> planRepository.findById(p.planId()).orElse(null)));
        registration.setAdminEmail(p.email());
        registration.setAdminFirstName(firstName);
        registration.setAdminLastName(lastName);
        registration.setAdminPassword(request.password());
        boolean provisioned = tenant.getStatus() == TenantStatus.ACTIVE;
        executor.execute(() -> {
            try {
                if (!provisioned) {
                    provisioningService.provisionTenant(registration);
                }
                applyTimezone(tenant.getSubdomain(), registration.getTimezone());
                // The trial starts when the workspace is ready, not when the email was confirmed.
                if (subscriptionRepository.findByTenantId(id).isEmpty()) {
                    subscriptionService.createSubscription(
                        new CreateSubscriptionRequest(
                            id,
                            p.planId(),
                            BillingCycle.MONTHLY,
                            // Not renewing: the trial only turns into a paid plan once someone chooses one.
                            false,
                            Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS),
                            null
                        )
                    );
                }
                mailService.sendWelcome(p.email(), tenant.getName(), tenant.getSubdomain(), firstName, true);
                log.info("Self-serve workspace '{}' is ready; trial started", tenant.getSubdomain());
            } catch (Exception e) {
                // provisionTenant has recorded PROVISIONING_FAILED; the link retries from there.
                log.error("Self-serve workspace '{}' setup failed", tenant.getSubdomain(), e);
            } finally {
                running.remove(id);
            }
        });
    }

    /**
     * Company settings read the timezone from the workspace's own settings row, which does not exist
     * until someone saves it (until then they show UTC): start it with the zone the sign-up came from.
     */
    private void applyTimezone(String subdomain, String timezone) {
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(subdomain);
        try {
            tenantTx.executeWithoutResult(tx -> {
                if (settingsRepository.count() == 0) {
                    OrganizationSettings settings = new OrganizationSettings();
                    settings.setTimezone(timezone);
                    settingsRepository.save(settings);
                }
            });
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private SignupView toView(SignupLink.Payload p, Tenant tenant) {
        String planName = masterTx.execute(tx -> planRepository.findById(p.planId()).map(SignupService::planName).orElse(null));
        return new SignupView(
            tenant == null ? SignupView.Stage.CONFIRM : stage(tenant),
            p.email(),
            p.companyName(),
            p.subdomain(),
            planName,
            TRIAL_DAYS
        );
    }

    /** Ready once the trial exists; setting up while this instance works on it; otherwise it needs a retry. */
    private SignupView.Stage stage(Tenant tenant) {
        boolean busy = running.contains(tenant.getId());
        if (tenant.getStatus() == TenantStatus.PROVISIONING_FAILED) {
            return busy ? SignupView.Stage.SETTING_UP : SignupView.Stage.FAILED;
        }
        if (SETTING_UP.contains(tenant.getStatus())) {
            return busy ? SignupView.Stage.SETTING_UP : SignupView.Stage.FAILED;
        }
        if (tenant.getStatus() == TenantStatus.ACTIVE && subscriptionRepository.findByTenantId(tenant.getId()).isEmpty()) {
            return busy ? SignupView.Stage.SETTING_UP : SignupView.Stage.FAILED;
        }
        return SignupView.Stage.READY;
    }

    /** The workspace this link created, if any. Someone else's workspace at that address means the address was taken. */
    private Optional<Tenant> ownTenant(SignupLink.Payload p) {
        Optional<Tenant> found = tenantRepository.findBySubdomain(p.subdomain());
        if (found.isEmpty()) {
            return found;
        }
        Tenant t = found.orElseThrow();
        if (t.isDemo() || GONE.contains(t.getStatus()) || !p.email().equalsIgnoreCase(t.getDemoContactEmail())) {
            throw new BadRequestAlertException("This workspace address was taken in the meantime", ENTITY_NAME, "subdomaintaken");
        }
        return found;
    }

    /** A live self-serve workspace already signed up with this address. */
    private Optional<Tenant> ownWorkspace(String email) {
        return tenantRepository
            .findByDemoContactEmailIgnoreCaseAndDemoFalse(email)
            .stream()
            .filter(t -> !GONE.contains(t.getStatus()))
            .findFirst();
    }

    private SubscriptionPlan planOnSale(UUID planId) {
        return planRepository
            .findById(planId)
            .filter(p -> p.isActive() && p.isPublicListed())
            .orElseThrow(() -> new BadRequestAlertException("This plan is not available", ENTITY_NAME, "planunavailable"));
    }

    private static String planName(SubscriptionPlan plan) {
        return plan.getDisplayName() != null ? plan.getDisplayName() : String.valueOf(plan.getSubscriptionType());
    }

    /** Shared inbox providers: their domain says nothing about the company. */
    private static final Set<String> FREE_MAIL_DOMAINS = Set.of(
        "gmail.com",
        "googlemail.com",
        "outlook.com",
        "hotmail.com",
        "live.com",
        "yahoo.com",
        "icloud.com",
        "me.com",
        "aol.com",
        "proton.me",
        "protonmail.com",
        "gmx.com",
        "gmx.de",
        "yandex.com",
        "mail.com"
    );

    /**
     * The company's website domain: the work email's, unless it is a shared inbox provider or another
     * workspace already has it (the column is unique) — then a placeholder, as for demos.
     */
    private String companyDomain(String email, String subdomain) {
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!FREE_MAIL_DOMAINS.contains(domain) && !DISPOSABLE_DOMAINS.contains(domain) && !tenantRepository.existsByDomain(domain)) {
            return domain;
        }
        return domainFor(subdomain);
    }

    /** Self-serve workspaces have no domain of their own; the unique column gets a placeholder, as demos do. */
    private static String domainFor(String subdomain) {
        return subdomain + ".workspace";
    }

    private static String validZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return "UTC";
        }
        return TimeZone.getTimeZone(zone.trim()).getID().equals("GMT") && !zone.trim().equals("GMT") ? "UTC" : zone.trim();
    }
}
