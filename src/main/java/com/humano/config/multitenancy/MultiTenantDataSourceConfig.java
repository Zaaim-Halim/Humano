package com.humano.config.multitenancy;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for multi-tenant data sources.
 * Sets up the master data source and tenant routing data source.
 *
 * @author Humano Team
 */
@Configuration
public class MultiTenantDataSourceConfig {

    /**
     * Master data source for tenant/billing management.
     * This connects to the centralized humano_master_db database.
     *
     * @return the master DataSource
     */
    @Primary
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Dynamic tenant data source that routes to appropriate tenant database.
     * Each tenant database can be on the same or different server.
     *
     * @param masterDataSource the master data source
     * @param tenantDataSourceProvider the tenant data source provider
     * @return the tenant routing DataSource
     */
    @Bean(name = "tenantDataSource")
    public DataSource tenantDataSource(
        @Qualifier("masterDataSource") DataSource masterDataSource,
        TenantDataSourceProvider tenantDataSourceProvider
    ) {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.setTenantDataSourceProvider(tenantDataSourceProvider);

        return routingDataSource;
    }
}
