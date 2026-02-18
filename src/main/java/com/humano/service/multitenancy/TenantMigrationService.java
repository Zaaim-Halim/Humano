package com.humano.service.multitenancy;

import com.humano.config.multitenancy.TenantDataSourceProvider;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles Liquibase migrations for tenant databases.
 *
 * @author Humano Team
 */
@Service
public class TenantMigrationService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantMigrationService.class);
    private static final String TENANT_CHANGELOG = "classpath:config/liquibase/tenant/tenant.xml";

    private final TenantDataSourceProvider dataSourceProvider;

    public TenantMigrationService(TenantDataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    /**
     * Runs tenant-specific migrations on the tenant's dedicated database.
     *
     * @param tenant the tenant
     * @throws Exception if migrations fail
     */
    public void runMigrations(Tenant tenant) throws Exception {
        TenantDatabaseConfig dbConfig = tenant.getDatabaseConfig();
        LOG.info("Running migrations for tenant {} on database {}", tenant.getSubdomain(), dbConfig.getDbName());

        DataSource tenantDataSource = dataSourceProvider.getOrCreateDataSource(tenant.getSubdomain());

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(tenantDataSource);
        liquibase.setChangeLog(TENANT_CHANGELOG);
        liquibase.setContexts("tenant");
        liquibase.setShouldRun(true);
        liquibase.setDropFirst(false);

        liquibase.afterPropertiesSet();

        LOG.info("Successfully completed migrations for tenant {}", tenant.getSubdomain());
    }

    /**
     * Runs migrations for all active tenants.
     * Useful for applying updates across all tenant databases.
     *
     * @param tenants the tenants to migrate
     */
    public void runMigrationsForAllTenants(Iterable<Tenant> tenants) {
        for (Tenant tenant : tenants) {
            try {
                if (tenant.getDatabaseConfig() != null) {
                    runMigrations(tenant);
                }
            } catch (Exception e) {
                LOG.error("Failed to run migrations for tenant {}: {}", tenant.getSubdomain(), e.getMessage());
            }
        }
    }
}
