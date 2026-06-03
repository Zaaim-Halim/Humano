package com.humano.service.multitenancy;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantPasswordCipher;
import com.humano.domain.enumeration.tenant.ProvisioningStep;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.tenant.TenantDatabaseConfigRepository;
import com.humano.repository.tenant.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the complete tenant provisioning workflow:
 * 1. Creates tenant record in master database
 * 2. Determines which database server to use (same or different)
 * 3. Creates dedicated tenant database
 * 4. Runs Liquibase migrations for tenant database
 * 5. Creates default admin user for tenant
 * 6. Initializes default configuration
 *
 * @author Humano Team
 */
@Service
public class TenantProvisioningService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final TenantRepository tenantRepository;
    private final TenantDatabaseConfigRepository databaseConfigRepository;
    private final TenantDatabaseManager databaseManager;
    private final TenantMigrationService migrationService;
    private final TenantInitializationService initializationService;
    private final DatabaseServerSelector serverSelector;
    private final MultiTenantProperties properties;
    private final TenantPasswordCipher passwordCipher;

    public TenantProvisioningService(
        TenantRepository tenantRepository,
        TenantDatabaseConfigRepository databaseConfigRepository,
        TenantDatabaseManager databaseManager,
        TenantMigrationService migrationService,
        TenantInitializationService initializationService,
        DatabaseServerSelector serverSelector,
        MultiTenantProperties properties,
        TenantPasswordCipher passwordCipher
    ) {
        this.tenantRepository = tenantRepository;
        this.databaseConfigRepository = databaseConfigRepository;
        this.databaseManager = databaseManager;
        this.migrationService = migrationService;
        this.initializationService = initializationService;
        this.serverSelector = serverSelector;
        this.properties = properties;
        this.passwordCipher = passwordCipher;
    }

    /**
     * Provisions a new tenant with a dedicated database, or resumes an in-progress / failed
     * provisioning for the same subdomain (P1.6). Each step is idempotent:
     * <ul>
     *   <li>tenant + db_config rows are reused if they exist;</li>
     *   <li>{@code CREATE DATABASE IF NOT EXISTS} / {@code CREATE USER IF NOT EXISTS} in
     *       {@link TenantDatabaseManager};</li>
     *   <li>Liquibase skips already-applied changesets via {@code DATABASECHANGELOG};</li>
     *   <li>{@link TenantInitializationService} guards each seed with an existence check.</li>
     * </ul>
     * The highest completed step is persisted on {@code tenant.provisioning_step} after each
     * successful step so resumes can short-circuit work that's already done.
     */
    @Transactional
    public Tenant provisionTenant(TenantRegistrationDTO registrationDTO) {
        String subdomain = registrationDTO.getSubdomain().toLowerCase();
        LOG.info("Starting provisioning for tenant: {}", subdomain);

        Optional<Tenant> existing = tenantRepository.findBySubdomain(subdomain);
        Tenant tenant;
        if (existing.isPresent()) {
            Tenant t = existing.get();
            TenantStatus status = t.getStatus();
            // Resume only from non-terminal in-progress states.
            if (status == TenantStatus.ACTIVE || status == TenantStatus.SUSPENDED) {
                throw new TenantProvisioningException("Subdomain already exists: " + subdomain);
            }
            if (status == TenantStatus.DEACTIVATED || status == TenantStatus.DELETED) {
                throw new TenantProvisioningException(
                    "Subdomain '" + subdomain + "' belongs to a deactivated/deleted tenant; pick a different subdomain"
                );
            }
            LOG.info("Resuming provisioning for tenant '{}' from step {}", subdomain, t.getProvisioningStep());
            tenant = t;
        } else {
            tenant = createTenantRecord(registrationDTO);
            markStep(tenant, ProvisioningStep.TENANT_CREATED);
        }

        try {
            // Step 2/3: Select server + create the db_config row (idempotent: reuse existing).
            TenantDatabaseConfig dbConfig = tenant.getDatabaseConfig();
            if (dbConfig == null) {
                DatabaseServerInfo serverInfo = serverSelector.selectServer(registrationDTO);
                LOG.info("Selected database server for tenant {}: {}:{}", subdomain, serverInfo.host(), serverInfo.port());
                dbConfig = databaseConfigRepository.save(createDatabaseConfig(tenant, serverInfo));
                tenant.setDatabaseConfig(dbConfig);
            }
            tenant.setStatus(TenantStatus.PROVISIONING);
            markStep(tenant, ProvisioningStep.CONFIG_CREATED);

            // Step 4: physical DB + DB user (CREATE … IF NOT EXISTS, safe to re-run).
            databaseManager.createDatabase(dbConfig);
            markStep(tenant, ProvisioningStep.DATABASE_CREATED);

            // Step 5: Liquibase tracks applied changesets in DATABASECHANGELOG.
            migrationService.runMigrations(tenant);
            markStep(tenant, ProvisioningStep.MIGRATIONS_RUN);

            // Step 6: each seed is guarded by an existence check (see TenantInitializationService).
            initializationService.initializeTenant(tenant, registrationDTO);
            markStep(tenant, ProvisioningStep.INITIALIZED);

            // Step 7: terminal state.
            tenant.setStatus(TenantStatus.ACTIVE);
            markStep(tenant, ProvisioningStep.COMPLETED);
            LOG.info(
                "Successfully provisioned tenant: {} on {}:{}/{}",
                subdomain,
                dbConfig.getDbHost(),
                dbConfig.getDbPort(),
                dbConfig.getDbName()
            );
            return tenant;
        } catch (Exception e) {
            LOG.error("Failed to provision tenant '{}' at step {}; leaving rows for resume", subdomain, tenant.getProvisioningStep(), e);
            tenant.setStatus(TenantStatus.PROVISIONING_FAILED);
            tenantRepository.save(tenant);
            // NOTE: we deliberately do NOT dropDatabaseIfExists here — destroying partial state
            // would defeat the point of resumable provisioning. Operators clean up explicitly
            // via deprovisionTenant() if the failure is unrecoverable.
            throw new TenantProvisioningException("Failed to provision tenant: " + subdomain, e);
        }
    }

    /** Persist the new highest-completed step. Skips downgrades. */
    private void markStep(Tenant tenant, ProvisioningStep step) {
        ProvisioningStep current = tenant.getProvisioningStep();
        if (current == null || step.ordinal() > current.ordinal()) {
            tenant.setProvisioningStep(step);
        }
        tenantRepository.save(tenant);
    }

    /**
     * Deprovisions a tenant and removes their database.
     *
     * @param tenantId the tenant ID
     */
    @Transactional
    public void deprovisionTenant(UUID tenantId) {
        Tenant tenant = tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new TenantProvisioningException("Tenant not found: " + tenantId));

        LOG.warn("Deprovisioning tenant: {}", tenant.getSubdomain());

        try {
            // Drop the tenant's database
            if (tenant.getDatabaseConfig() != null) {
                databaseManager.dropDatabaseIfExists(tenant.getDatabaseConfig());
                databaseConfigRepository.delete(tenant.getDatabaseConfig());
            }

            // Update tenant status
            tenant.setStatus(TenantStatus.DEACTIVATED);
            tenant.setDatabaseConfig(null);
            tenantRepository.save(tenant);

            LOG.info("Successfully deprovisioned tenant: {}", tenant.getSubdomain());
        } catch (Exception e) {
            LOG.error("Failed to deprovision tenant: {}", tenant.getSubdomain(), e);
            throw new TenantProvisioningException("Failed to deprovision tenant: " + tenant.getSubdomain(), e);
        }
    }

    /**
     * Suspends a tenant (keeps database but marks as inactive).
     *
     * @param tenantId the tenant ID
     */
    @Transactional
    public void suspendTenant(UUID tenantId) {
        Tenant tenant = tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new TenantProvisioningException("Tenant not found: " + tenantId));

        LOG.info("Suspending tenant: {}", tenant.getSubdomain());
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);
    }

    /**
     * Activates a suspended tenant.
     *
     * @param tenantId the tenant ID
     */
    @Transactional
    public void activateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new TenantProvisioningException("Tenant not found: " + tenantId));

        if (tenant.getStatus() != TenantStatus.SUSPENDED) {
            throw new TenantProvisioningException("Tenant is not suspended: " + tenant.getSubdomain());
        }

        LOG.info("Activating tenant: {}", tenant.getSubdomain());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
    }

    private Tenant createTenantRecord(TenantRegistrationDTO dto) {
        Tenant tenant = new Tenant();
        tenant.setName(dto.getCompanyName());
        tenant.setDomain(dto.getDomain());
        tenant.setSubdomain(dto.getSubdomain().toLowerCase());
        if (dto.getTimezone() != null) {
            tenant.setTimezone(java.util.TimeZone.getTimeZone(dto.getTimezone()));
        }
        tenant.setStatus(TenantStatus.PENDING_SETUP);
        tenant.setSubscriptionPlan(dto.getSubscriptionPlan());
        return tenantRepository.save(tenant);
    }

    private TenantDatabaseConfig createDatabaseConfig(Tenant tenant, DatabaseServerInfo serverInfo) {
        TenantDatabaseConfig config = new TenantDatabaseConfig();
        config.setTenant(tenant);
        config.setDbHost(serverInfo.host());
        config.setDbPort(serverInfo.port());
        config.setDbName(properties.getTenantDatabasePrefix() + tenant.getSubdomain().toLowerCase().replaceAll("[^a-z0-9]", "_"));
        config.setDbUsername(generateDbUsername(tenant));
        // Plaintext is generated, used once at DB-user creation in TenantDatabaseManager (the
        // bcrypt-/SHA-style hash MySQL stores is opaque to us), and never persisted. What we
        // store in tenant_database_config.db_password is the Jasypt-encrypted form, which
        // TenantDataSourceProvider decrypts when wiring each tenant's Hikari pool.
        config.setDbPassword(passwordCipher.encrypt(passwordCipher.generatePassword()));
        config.setConnectionParams(properties.getDefaultConnectionParams());
        config.setRegion(serverInfo.region());
        config.setMaxPoolSize(determinePoolSize(tenant));
        config.setDedicatedServer(serverInfo.dedicated());
        config.setServerGroup(serverInfo.serverGroup());
        return config;
    }

    private String generateDbUsername(Tenant tenant) {
        return "tenant_" + tenant.getSubdomain().toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    private int determinePoolSize(Tenant tenant) {
        // Determine pool size based on subscription plan
        if (tenant.getSubscriptionPlan() == null) {
            return properties.getDefaultMaxPoolSize();
        }

        String planName = tenant.getSubscriptionPlan().getSubscriptionType() != null
            ? tenant.getSubscriptionPlan().getSubscriptionType().name().toLowerCase()
            : "";

        return switch (planName) {
            case "enterprise" -> 50;
            case "professional", "premium" -> 20;
            case "starter", "basic" -> 10;
            default -> properties.getDefaultMaxPoolSize();
        };
    }
}
