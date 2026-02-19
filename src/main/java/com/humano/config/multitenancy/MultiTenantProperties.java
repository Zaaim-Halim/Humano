package com.humano.config.multitenancy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for multi-tenant database setup.
 *
 * @author Humano Team
 */
@Component
@ConfigurationProperties(prefix = "humano.multitenancy")
public class MultiTenantProperties {

    private boolean enabled = true;
    private String masterDatabase = "humano_master_db";
    private String tenantDatabasePrefix = "humano_tenant_";
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    // Default connection pool settings for tenant databases
    private int defaultMaxPoolSize = 10;
    private int defaultMinIdle = 2;
    private long connectionTimeout = 30000;
    private long idleTimeout = 600000;
    private long maxLifetime = 1800000;

    private boolean cachePrepStmts;
    private int prepStmtCacheSize;
    private int prepStmtCacheSqlLimit;

    // Default connection parameters
    private String defaultConnectionParams =
        "useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";

    // Default database server for new tenants
    private String defaultDbHost = "localhost";
    private int defaultDbPort = 3306;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMasterDatabase() {
        return masterDatabase;
    }

    public void setMasterDatabase(String masterDatabase) {
        this.masterDatabase = masterDatabase;
    }

    public String getTenantDatabasePrefix() {
        return tenantDatabasePrefix;
    }

    public void setTenantDatabasePrefix(String tenantDatabasePrefix) {
        this.tenantDatabasePrefix = tenantDatabasePrefix;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public int getDefaultMaxPoolSize() {
        return defaultMaxPoolSize;
    }

    public void setDefaultMaxPoolSize(int defaultMaxPoolSize) {
        this.defaultMaxPoolSize = defaultMaxPoolSize;
    }

    public int getDefaultMinIdle() {
        return defaultMinIdle;
    }

    public void setDefaultMinIdle(int defaultMinIdle) {
        this.defaultMinIdle = defaultMinIdle;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public String getDefaultConnectionParams() {
        return defaultConnectionParams;
    }

    public void setDefaultConnectionParams(String defaultConnectionParams) {
        this.defaultConnectionParams = defaultConnectionParams;
    }

    public String getDefaultDbHost() {
        return defaultDbHost;
    }

    public void setDefaultDbHost(String defaultDbHost) {
        this.defaultDbHost = defaultDbHost;
    }

    public int getDefaultDbPort() {
        return defaultDbPort;
    }

    public void setDefaultDbPort(int defaultDbPort) {
        this.defaultDbPort = defaultDbPort;
    }

    public boolean isCachePrepStmts() {
        return cachePrepStmts;
    }

    public void setCachePrepStmts(boolean cachePrepStmts) {
        this.cachePrepStmts = cachePrepStmts;
    }

    public int getPrepStmtCacheSize() {
        return prepStmtCacheSize;
    }

    public void setPrepStmtCacheSize(int prepStmtCacheSize) {
        this.prepStmtCacheSize = prepStmtCacheSize;
    }

    public int getPrepStmtCacheSqlLimit() {
        return prepStmtCacheSqlLimit;
    }

    public void setPrepStmtCacheSqlLimit(int prepStmtCacheSqlLimit) {
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
    }
}
