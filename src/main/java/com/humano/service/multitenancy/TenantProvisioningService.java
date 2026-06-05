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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate masterTx;

    public TenantProvisioningService(
        TenantRepository tenantRepository,
        TenantDatabaseConfigRepository databaseConfigRepository,
        TenantDatabaseManager databaseManager,
        TenantMigrationService migrationService,
        TenantInitializationService initializationService,
        DatabaseServerSelector serverSelector,
        MultiTenantProperties properties,
        TenantPasswordCipher passwordCipher,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.databaseConfigRepository = databaseConfigRepository;
        this.databaseManager = databaseManager;
        this.migrationService = migrationService;
        this.initializationService = initializationService;
        this.serverSelector = serverSelector;
        this.properties = properties;
        this.passwordCipher = passwordCipher;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
    }

    /**
     * Provisions a new tenant with a dedicated database, or resumes an in-progress / failed
     * provisioning for the same subdomain (P1.6 / P1.10). Each step is idempotent:
     * <ul>
     *   <li>tenant + db_config rows are reused if they exist;</li>
     *   <li>{@code CREATE DATABASE IF NOT EXISTS} / {@code CREATE USER IF NOT EXISTS} in
     *       {@link TenantDatabaseManager};</li>
     *   <li>Liquibase skips already-applied changesets via {@code DATABASECHANGELOG};</li>
     *   <li>{@link TenantInitializationService} guards each seed with an existence check.</li>
     * </ul>
     *
     * <p><b>P1.10 — crash safety.</b> This method is deliberately NOT {@code @Transactional}.
     * The DDL inside {@link TenantDatabaseManager#createDatabase} and the Liquibase run inside
     * {@link TenantMigrationService#runMigrations} autocommit and so escape any wrapping JPA
     * transaction. If a master TX were wrapping this method, {@code markStep} writes would
     * buffer until method return, and the {@code catch} block's
     * {@code setStatus(PROVISIONING_FAILED) + save} would roll back with the rest — leaving
     * the physical tenant DB on disk but no master row to resume from.
     *
     * <p>Instead, each master-DB mutation runs in its own short {@link TransactionTemplate}
     * commit bound to {@code masterTransactionManager}, between which the long-running DDL
     * / migration / seed steps execute. On exception we commit a final
     * {@code PROVISIONING_FAILED} status update independently.
     */
    public Tenant provisionTenant(TenantRegistrationDTO registrationDTO) {
        String subdomain = registrationDTO.getSubdomain().toLowerCase();
        LOG.info("Starting provisioning for tenant: {}", subdomain);

        // Step 1: resolve OR create the master tenant row. Committed independently.
        UUID tenantId = masterTx.execute(tx -> {
            Optional<Tenant> existing = tenantRepository.findBySubdomain(subdomain);
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
                return t.getId();
            }
            Tenant created = createTenantRecord(registrationDTO);
            applyStep(created, ProvisioningStep.TENANT_CREATED);
            return tenantRepository.save(created).getId();
        });

        try {
            // Step 2/3: select server + create the db_config row + flip status to PROVISIONING.
            // All three persist together in one committed tx so a crash between them is impossible.
            TenantDatabaseConfig dbConfig = masterTx.execute(tx -> {
                Tenant t = loadTenant(tenantId);
                TenantDatabaseConfig cfg = t.getDatabaseConfig();
                if (cfg == null) {
                    DatabaseServerInfo serverInfo = serverSelector.selectServer(registrationDTO);
                    LOG.info("Selected database server for tenant {}: {}:{}", subdomain, serverInfo.host(), serverInfo.port());
                    cfg = databaseConfigRepository.save(createDatabaseConfig(t, serverInfo));
                    t.setDatabaseConfig(cfg);
                }
                t.setStatus(TenantStatus.PROVISIONING);
                applyStep(t, ProvisioningStep.CONFIG_CREATED);
                tenantRepository.save(t);
                return cfg;
            });

            // Step 4: physical DB + DB user (CREATE … IF NOT EXISTS, safe to re-run). DDL, no tx.
            databaseManager.createDatabase(dbConfig);
            markStep(tenantId, ProvisioningStep.DATABASE_CREATED);

            // Step 5: Liquibase tracks applied changesets in DATABASECHANGELOG. Runs its own
            // connection/transaction internally; not part of any master tx.
            Tenant snapshotForMigration = loadInTx(tenantId);
            migrationService.runMigrations(snapshotForMigration);
            markStep(tenantId, ProvisioningStep.MIGRATIONS_RUN);

            // Step 6: tenant-DB seed. initializeTenant drives its own TransactionTemplate bound
            // to tenantTransactionManager — independent from the master TM used here.
            initializationService.initializeTenant(snapshotForMigration, registrationDTO);
            markStep(tenantId, ProvisioningStep.INITIALIZED);

            // Step 7: terminal state. Commit the ACTIVE flip + COMPLETED step together.
            Tenant finalTenant = masterTx.execute(tx -> {
                Tenant t = loadTenant(tenantId);
                t.setStatus(TenantStatus.ACTIVE);
                applyStep(t, ProvisioningStep.COMPLETED);
                return tenantRepository.save(t);
            });
            LOG.info(
                "Successfully provisioned tenant: {} on {}:{}/{}",
                subdomain,
                dbConfig.getDbHost(),
                dbConfig.getDbPort(),
                dbConfig.getDbName()
            );
            return finalTenant;
        } catch (Exception e) {
            ProvisioningStep lastStep = safeReadStep(tenantId);
            LOG.error("Failed to provision tenant '{}' at step {}; leaving rows for resume", subdomain, lastStep, e);
            // Persist PROVISIONING_FAILED in its own committed tx so the failure state survives
            // even if the original failure aborted whatever tx the calling layer had open.
            try {
                masterTx.executeWithoutResult(tx -> {
                    Tenant t = tenantRepository.findById(tenantId).orElse(null);
                    if (t != null) {
                        t.setStatus(TenantStatus.PROVISIONING_FAILED);
                        tenantRepository.save(t);
                    }
                });
            } catch (Exception inner) {
                LOG.error("Also failed to persist PROVISIONING_FAILED status for tenant '{}'", subdomain, inner);
            }
            // NOTE: we deliberately do NOT dropDatabaseIfExists here — destroying partial state
            // would defeat the point of resumable provisioning. Operators clean up explicitly
            // via deprovisionTenant() if the failure is unrecoverable.
            throw new TenantProvisioningException("Failed to provision tenant: " + subdomain, e);
        }
    }

    /** Look up a tenant by id inside the current tx; fail loudly if it's gone. */
    private Tenant loadTenant(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new TenantProvisioningException("Tenant disappeared mid-provision: " + id));
    }

    /** Wrap a tenant load in its own short tx — used when we need a snapshot to hand off to a non-tx step. */
    private Tenant loadInTx(UUID id) {
        return masterTx.execute(tx -> loadTenant(id));
    }

    /** Apply the new highest-completed step to an attached entity. Skips downgrades. */
    private void applyStep(Tenant tenant, ProvisioningStep step) {
        ProvisioningStep current = tenant.getProvisioningStep();
        if (current == null || step.ordinal() > current.ordinal()) {
            tenant.setProvisioningStep(step);
        }
    }

    /** Standalone committed write: reload, apply, save. Use between non-tx pipeline steps. */
    private void markStep(UUID tenantId, ProvisioningStep step) {
        masterTx.executeWithoutResult(tx -> {
            Tenant t = loadTenant(tenantId);
            applyStep(t, step);
            tenantRepository.save(t);
        });
    }

    /** Best-effort read for log context after a failure. Never throws. */
    private ProvisioningStep safeReadStep(UUID tenantId) {
        try {
            return masterTx.execute(tx -> tenantRepository.findById(tenantId).map(Tenant::getProvisioningStep).orElse(null));
        } catch (Exception ignored) {
            return null;
        }
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
