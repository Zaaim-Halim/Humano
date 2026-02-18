package com.humano.service.multitenancy;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.tenant.TenantDatabaseConfigRepository;
import com.humano.repository.tenant.TenantRepository;
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

    public TenantProvisioningService(
        TenantRepository tenantRepository,
        TenantDatabaseConfigRepository databaseConfigRepository,
        TenantDatabaseManager databaseManager,
        TenantMigrationService migrationService,
        TenantInitializationService initializationService,
        DatabaseServerSelector serverSelector,
        MultiTenantProperties properties
    ) {
        this.tenantRepository = tenantRepository;
        this.databaseConfigRepository = databaseConfigRepository;
        this.databaseManager = databaseManager;
        this.migrationService = migrationService;
        this.initializationService = initializationService;
        this.serverSelector = serverSelector;
        this.properties = properties;
    }

    /**
     * Provisions a new tenant with a dedicated database.
     *
     * @param registrationDTO the tenant registration details
     * @return the provisioned tenant
     */
    @Transactional
    public Tenant provisionTenant(TenantRegistrationDTO registrationDTO) {
        LOG.info("Starting provisioning for tenant: {}", registrationDTO.getSubdomain());

        // Validate subdomain is not already taken
        if (tenantRepository.existsBySubdomain(registrationDTO.getSubdomain())) {
            throw new TenantProvisioningException("Subdomain already exists: " + registrationDTO.getSubdomain());
        }

        // Step 1: Create tenant record in master database
        Tenant tenant = createTenantRecord(registrationDTO);

        try {
            // Step 2: Select database server (same server, different server, or dedicated)
            DatabaseServerInfo serverInfo = serverSelector.selectServer(registrationDTO);
            LOG.info("Selected database server for tenant {}: {}:{}", tenant.getSubdomain(), serverInfo.host(), serverInfo.port());

            // Step 3: Create database configuration
            TenantDatabaseConfig dbConfig = createDatabaseConfig(tenant, serverInfo);
            dbConfig = databaseConfigRepository.save(dbConfig);
            tenant.setDatabaseConfig(dbConfig);
            tenant.setStatus(TenantStatus.PROVISIONING);
            tenantRepository.save(tenant);

            // Step 4: Create the physical database on the selected server
            databaseManager.createDatabase(dbConfig);

            // Step 5: Run migrations on new database
            migrationService.runMigrations(tenant);

            // Step 6: Initialize tenant with default data
            initializationService.initializeTenant(tenant);

            // Step 7: Mark tenant as active
            tenant.setStatus(TenantStatus.ACTIVE);
            LOG.info(
                "Successfully provisioned tenant: {} on {}:{}/{}",
                tenant.getSubdomain(),
                dbConfig.getDbHost(),
                dbConfig.getDbPort(),
                dbConfig.getDbName()
            );

            return tenantRepository.save(tenant);
        } catch (Exception e) {
            LOG.error("Failed to provision tenant: {}", tenant.getSubdomain(), e);

            // Rollback: mark tenant as failed and cleanup
            tenant.setStatus(TenantStatus.PROVISIONING_FAILED);
            tenantRepository.save(tenant);

            // Attempt to cleanup the database if it was created
            if (tenant.getDatabaseConfig() != null) {
                try {
                    databaseManager.dropDatabaseIfExists(tenant.getDatabaseConfig());
                } catch (Exception cleanupEx) {
                    LOG.error("Failed to cleanup database for tenant: {}", tenant.getSubdomain(), cleanupEx);
                }
            }

            throw new TenantProvisioningException("Failed to provision tenant: " + tenant.getSubdomain(), e);
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
        config.setDbPassword(generateSecurePassword());
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

    private String generateSecurePassword() {
        // Generate a secure random password
        return java.util.UUID.randomUUID().toString().replace("-", "");
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
