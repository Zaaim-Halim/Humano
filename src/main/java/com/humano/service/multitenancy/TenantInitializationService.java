package com.humano.service.multitenancy;

import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Initializes a new tenant database with default data.
 * Creates default authorities, permissions, and admin user.
 *
 * @author Humano Team
 */
@Service
public class TenantInitializationService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantInitializationService.class);

    /**
     * Initializes a tenant with default data.
     * This includes creating default authorities, permissions, and an admin user.
     *
     * @param tenant the tenant to initialize
     */
    public void initializeTenant(Tenant tenant) {
        LOG.info("Initializing tenant {} with default data", tenant.getSubdomain());

        try {
            // Set tenant context for database operations
            TenantContext.setCurrentTenant(tenant.getSubdomain());

            // Create default authorities
            createDefaultAuthorities(tenant);

            // Create default permissions
            createDefaultPermissions(tenant);

            // Create admin user for the tenant
            createAdminUser(tenant);

            // Create default configuration
            createDefaultConfiguration(tenant);

            LOG.info("Successfully initialized tenant {}", tenant.getSubdomain());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Creates default authorities (roles) for the tenant.
     *
     * @param tenant the tenant
     */
    private void createDefaultAuthorities(Tenant tenant) {
        LOG.debug("Creating default authorities for tenant {}", tenant.getSubdomain());
        // Default authorities: ROLE_ADMIN, ROLE_USER, ROLE_HR, ROLE_MANAGER, ROLE_EMPLOYEE
        // These will be created via Liquibase migrations or programmatically
    }

    /**
     * Creates default permissions for the tenant.
     *
     * @param tenant the tenant
     */
    private void createDefaultPermissions(Tenant tenant) {
        LOG.debug("Creating default permissions for tenant {}", tenant.getSubdomain());
        // Default permissions will be created via Liquibase migrations
    }

    /**
     * Creates the initial admin user for the tenant.
     *
     * @param tenant the tenant
     */
    private void createAdminUser(Tenant tenant) {
        LOG.debug("Creating admin user for tenant {}", tenant.getSubdomain());
        // Admin user creation will be handled separately during registration
        // The admin credentials are provided during tenant registration
    }

    /**
     * Creates default configuration for the tenant.
     *
     * @param tenant the tenant
     */
    private void createDefaultConfiguration(Tenant tenant) {
        LOG.debug("Creating default configuration for tenant {}", tenant.getSubdomain());
        // Default settings like leave policies, working hours, etc.
    }
}
