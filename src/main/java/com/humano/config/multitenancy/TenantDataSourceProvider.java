package com.humano.config.multitenancy;

import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.repository.tenant.TenantRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Provides and caches DataSource instances for each tenant database.
 * Handles dynamic creation of tenant-specific database connections
 * to databases that may be on the SAME or DIFFERENT servers.
 *
 * @author Humano Team
 */
@Component
public class TenantDataSourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataSourceProvider.class);

    private final Map<String, HikariDataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final TenantRepository tenantRepository;
    private final MultiTenantProperties properties;

    public TenantDataSourceProvider(TenantRepository tenantRepository, MultiTenantProperties properties) {
        this.tenantRepository = tenantRepository;
        this.properties = properties;
    }

    /**
     * Gets or creates a DataSource for the specified tenant.
     * The DataSource connects to the tenant's dedicated database,
     * which may be on any server (same or different).
     *
     * @param tenantId the tenant identifier (subdomain)
     * @return the DataSource for the tenant
     */
    public DataSource getOrCreateDataSource(String tenantId) {
        return tenantDataSources.computeIfAbsent(tenantId, this::createDataSource);
    }

    private HikariDataSource createDataSource(String tenantId) {
        LOG.info("Creating DataSource for tenant: {}", tenantId);

        Tenant tenant = tenantRepository
            .findBySubdomain(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        TenantDatabaseConfig dbConfig = tenant.getDatabaseConfig();
        if (dbConfig == null) {
            throw new TenantNotFoundException("Database configuration not found for tenant: " + tenantId);
        }

        HikariConfig config = new HikariConfig();

        // Build JDBC URL pointing to tenant's specific database
        // This database can be on any server (same or different)
        config.setJdbcUrl(dbConfig.buildJdbcUrl());
        config.setUsername(dbConfig.getDbUsername());
        config.setPassword(decryptPassword(dbConfig.getDbPassword()));
        config.setDriverClassName(properties.getDriverClassName());

        // Connection pool settings
        config.setMaximumPoolSize(dbConfig.getMaxPoolSize() != null ? dbConfig.getMaxPoolSize() : properties.getDefaultMaxPoolSize());
        config.setMinimumIdle(properties.getDefaultMinIdle());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        config.setIdleTimeout(properties.getIdleTimeout());
        config.setMaxLifetime(properties.getMaxLifetime());
        config.setPoolName("HikariPool-Tenant-" + tenantId);

        // Connection test query
        config.setConnectionTestQuery("SELECT 1");

        // Additional properties for reliability
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        LOG.info(
            "Created DataSource for tenant {} connecting to {}:{}/{}",
            tenantId,
            dbConfig.getDbHost(),
            dbConfig.getDbPort(),
            dbConfig.getDbName()
        );

        return new HikariDataSource(config);
    }

    /**
     * Evicts and closes a tenant's DataSource.
     * Call this when a tenant is deactivated or their DB config changes.
     *
     * @param tenantId the tenant identifier
     */
    public void evictDataSource(String tenantId) {
        HikariDataSource dataSource = tenantDataSources.remove(tenantId);
        if (dataSource != null) {
            LOG.info("Evicting DataSource for tenant: {}", tenantId);
            dataSource.close();
        }
    }

    /**
     * Refreshes a tenant's DataSource (e.g., after DB migration to new server).
     *
     * @param tenantId the tenant identifier
     */
    public void refreshDataSource(String tenantId) {
        evictDataSource(tenantId);
        getOrCreateDataSource(tenantId);
    }

    /**
     * Health check - validates all active tenant connections.
     * Runs periodically to detect and handle connection issues.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void healthCheck() {
        tenantDataSources.forEach((tenantId, dataSource) -> {
            try {
                if (!dataSource.isRunning()) {
                    LOG.warn("DataSource for tenant {} is not running, evicting", tenantId);
                    evictDataSource(tenantId);
                }
            } catch (Exception e) {
                LOG.error("Health check failed for tenant {}: {}", tenantId, e.getMessage());
            }
        });
    }

    /**
     * Decrypts the database password.
     * For production, use Jasypt or similar encryption library.
     *
     * @param encryptedPassword the encrypted password
     * @return the decrypted password
     */
    private String decryptPassword(String encryptedPassword) {
        // TODO: Implement password decryption logic using Jasypt or similar
        // For now, return as-is (passwords should be encrypted in production)
        return encryptedPassword;
    }

    /**
     * Returns statistics about active tenant connections.
     *
     * @return map of tenant IDs to connection pool stats
     */
    public Map<String, ConnectionPoolStats> getPoolStats() {
        Map<String, ConnectionPoolStats> stats = new ConcurrentHashMap<>();
        tenantDataSources.forEach((tenantId, ds) -> {
            try {
                stats.put(
                    tenantId,
                    new ConnectionPoolStats(
                        ds.getHikariPoolMXBean().getActiveConnections(),
                        ds.getHikariPoolMXBean().getIdleConnections(),
                        ds.getHikariPoolMXBean().getTotalConnections(),
                        ds.getHikariPoolMXBean().getThreadsAwaitingConnection()
                    )
                );
            } catch (Exception e) {
                LOG.warn("Could not get pool stats for tenant {}: {}", tenantId, e.getMessage());
            }
        });
        return stats;
    }

    /**
     * Gets the number of active tenant data sources.
     *
     * @return count of active data sources
     */
    public int getActiveTenantCount() {
        return tenantDataSources.size();
    }

    /**
     * Checks if a data source exists for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return true if data source exists
     */
    public boolean hasDataSource(String tenantId) {
        return tenantDataSources.containsKey(tenantId);
    }

    /**
     * Record for connection pool statistics.
     */
    public record ConnectionPoolStats(int activeConnections, int idleConnections, int totalConnections, int threadsAwaiting) {}
}
