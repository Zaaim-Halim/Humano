package com.humano.config.multitenancy;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
     * Connection coordinates (url/user/password/driverClassName/type) for the master DB.
     * Routed through {@link DataSourceProperties} so {@code url} is translated to Hikari's
     * {@code jdbcUrl} when the underlying type is HikariCP.
     */
    @Primary
    @Bean(name = "masterDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Master data source for tenant/billing management.
     * Connects to {@code humano_master_db}. Pool tuning binds from
     * {@code spring.datasource.master.hikari.*}.
     */
    @Primary
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master.hikari")
    public HikariDataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties masterDataSourceProperties) {
        return masterDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * Connection coordinates for the default tenant DB (used in dev / as fallback target).
     */
    @Bean(name = "defaultTenantDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.default-tenant")
    public DataSourceProperties defaultTenantDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Default tenant data source for development purposes.
     * Connects to {@code humano_tenant_default}. Pool tuning binds from
     * {@code spring.datasource.default-tenant.hikari.*}.
     */
    @Bean(name = "defaultTenantDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.default-tenant.hikari")
    public HikariDataSource defaultTenantDataSource(
        @Qualifier("defaultTenantDataSourceProperties") DataSourceProperties defaultTenantDataSourceProperties
    ) {
        return defaultTenantDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
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
