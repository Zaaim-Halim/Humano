package com.humano.config.multitenancy;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Custom routing data source that dynamically selects the appropriate
 * tenant database based on the current tenant context.
 *
 * Supports routing to databases on different servers.
 *
 * @author Humano Team
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private TenantDataSourceProvider tenantDataSourceProvider;

    @Override
    protected Object determineCurrentLookupKey() {
        String tenantId = TenantContext.getCurrentTenant();
        return tenantId != null ? tenantId : "master";
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null || "master".equals(tenantId)) {
            return (DataSource) getResolvedDefaultDataSource();
        }

        // Dynamically get or create tenant data source
        // This handles databases on same or different servers
        if (tenantDataSourceProvider != null) {
            return tenantDataSourceProvider.getOrCreateDataSource(tenantId);
        }

        return (DataSource) getResolvedDefaultDataSource();
    }

    public void setTenantDataSourceProvider(TenantDataSourceProvider provider) {
        this.tenantDataSourceProvider = provider;
    }

    public TenantDataSourceProvider getTenantDataSourceProvider() {
        return tenantDataSourceProvider;
    }
}
