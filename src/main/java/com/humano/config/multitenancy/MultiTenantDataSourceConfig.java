package com.humano.config.multitenancy;

import com.zaxxer.hikari.HikariDataSource;
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
 * Uses properties from both spring.datasource.* and humano.multitenancy.*
 *
 * @author Humano Team
 */
@Configuration
public class MultiTenantDataSourceConfig {

    /**
     * Master data source for tenant/billing management.
     * This connects to the centralized humano_master_db database.
     * Uses spring.datasource.master.* for connection URL/credentials
     * and humano.multitenancy.* for pool settings.
     *
     * @return the master DataSource
     */
    @Primary
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Default tenant data source for development purposes.
     * This connects to the default tenant database (humano).
     * Uses spring.datasource.default-tenant.* for connection URL/credentials
     * and humano.multitenancy.* for pool settings.
     *
     * @return the default tenant DataSource
     */
    @Bean(name = "defaultTenantDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.default-tenant")
    public DataSource defaultTenantDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Dynamic tenant data source that routes to appropriate tenant database.
     * Each tenant database can be on the same or different server.
     *
     * @param masterDataSource the master data source
     * @param defaultTenantDataSource the default tenant data source
     * @param tenantDataSourceProvider the tenant data source provider
     * @return the tenant routing DataSource
     */
    @Bean(name = "tenantDataSource")
    public DataSource tenantDataSource(
        @Qualifier("masterDataSource") DataSource masterDataSource,
        @Qualifier("defaultTenantDataSource") DataSource defaultTenantDataSource,
        TenantDataSourceProvider tenantDataSourceProvider
    ) {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("default", defaultTenantDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(defaultTenantDataSource);
        routingDataSource.setTenantDataSourceProvider(tenantDataSourceProvider);

        return routingDataSource;
    }
}
