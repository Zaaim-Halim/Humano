# Humano Multi-Tenant Architecture Guide

## Executive Summary

This document outlines a professional multi-tenant architecture for the Humano HR & Payroll Management System. The architecture implements a **Database-per-Tenant** model where each tenant has their own dedicated database, combined with a **Shared Master Database** for centralized platform management. Tenant databases can reside on the **same database server** or be distributed across **different database servers** for scalability and geographic distribution.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              HUMANO PLATFORM                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                    MASTER DATABASE (humano_master_db)                      │  │
│  │                      Server: db-master.humano.com                          │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐        │  │
│  │  │   Tenant    │  │ Subscription│  │   Invoice   │  │  Payment   │        │  │
│  │  │  Registry   │  │    Plan     │  │             │  │            │        │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘        │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐        │  │
│  │  │Subscription │  │   Feature   │  │   Coupon    │  │  Tenant    │        │  │
│  │  │             │  │             │  │             │  │  DB Config │        │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘        │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐│
│  │                         TENANT DATABASES                                     ││
│  │                                                                              ││
│  │  ┌─────────────────────────────────────────────────────────────────────┐    ││
│  │  │  DB Server 1: db-server-1.humano.com (Same Server / Region A)       │    ││
│  │  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │    ││
│  │  │  │ humano_tenant_a  │  │ humano_tenant_b  │  │ humano_tenant_c  │   │    ││
│  │  │  │                  │  │                  │  │                  │   │    ││
│  │  │  │ • User           │  │ • User           │  │ • User           │   │    ││
│  │  │  │ • Authority      │  │ • Authority      │  │ • Authority      │   │    ││
│  │  │  │ • Employee       │  │ • Employee       │  │ • Employee       │   │    ││
│  │  │  │ • Department     │  │ • Department     │  │ • Department     │   │    ││
│  │  │  │ • Payroll*       │  │ • Payroll*       │  │ • Payroll*       │   │    ││
│  │  │  │ • HR Entities*   │  │ • HR Entities*   │  │ • HR Entities*   │   │    ││
│  │  │  └──────────────────┘  └──────────────────┘  └──────────────────┘   │    ││
│  │  └─────────────────────────────────────────────────────────────────────┘    ││
│  │                                                                              ││
│  │  ┌─────────────────────────────────────────────────────────────────────┐    ││
│  │  │  DB Server 2: db-server-2.humano.com (Different Server / Region B)  │    ││
│  │  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │    ││
│  │  │  │ humano_tenant_d  │  │ humano_tenant_e  │  │ humano_tenant_f  │   │    ││
│  │  │  │                  │  │                  │  │                  │   │    ││
│  │  │  │ • User           │  │ • User           │  │ • User           │   │    ││
│  │  │  │ • Authority      │  │ • Authority      │  │ • Authority      │   │    ││
│  │  │  │ • Employee       │  │ • Employee       │  │ • Employee       │   │    ││
│  │  │  │ • Department     │  │ • Department     │  │ • Department     │   │    ││
│  │  │  │ • Payroll*       │  │ • Payroll*       │  │ • Payroll*       │   │    ││
│  │  │  │ • HR Entities*   │  │ • HR Entities*   │  │ • HR Entities*   │   │    ││
│  │  │  └──────────────────┘  └──────────────────┘  └──────────────────┘   │    ││
│  │  └─────────────────────────────────────────────────────────────────────┘    ││
│  │                                                                              ││
│  │  ┌─────────────────────────────────────────────────────────────────────┐    ││
│  │  │  DB Server N: db-server-n.humano.com (Enterprise / Dedicated)       │    ││
│  │  │  ┌──────────────────────────────────────────────────────────────┐   │    ││
│  │  │  │              humano_tenant_enterprise_xyz                     │   │    ││
│  │  │  │       (Dedicated server for large enterprise tenant)          │   │    ││
│  │  │  └──────────────────────────────────────────────────────────────┘   │    ││
│  │  └─────────────────────────────────────────────────────────────────────┘    ││
│  └─────────────────────────────────────────────────────────────────────────────┘│
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Key Benefits of Database-per-Tenant

| Benefit                     | Description                                                                                     |
| --------------------------- | ----------------------------------------------------------------------------------------------- |
| **Complete Data Isolation** | Each tenant's data is in a completely separate database - highest level of security             |
| **Flexible Deployment**     | Tenant DBs can be on same server (cost-effective) or different servers (performance/compliance) |
| **Geographic Distribution** | Place tenant databases in regions close to their users for lower latency                        |
| **Independent Scaling**     | Scale individual tenant databases based on their specific needs                                 |
| **Easy Backup/Restore**     | Backup and restore individual tenant databases independently                                    |
| **Compliance Ready**        | Meet data residency requirements by placing DBs in specific regions                             |
| **Simple Migration**        | Move tenant databases between servers without affecting others                                  |

---

## Database Distribution Strategy

### Master Database (`humano_master_db`)

The master database is a **single, centralized database** that stores all platform-level data:

| Entity Category             | Tables                                                                         | Purpose                                     |
| --------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------- |
| **Tenant Registry**         | `tenant`, `organization`, `tenant_database_config`                             | Core tenant metadata and DB connection info |
| **Billing & Subscriptions** | `subscription_plan`, `subscription`, `feature`, `invoice`, `payment`, `coupon` | Centralized billing for all tenants         |
| **Platform Configuration**  | `country`, `currency`, `exchange_rate`                                         | Shared reference data                       |
| **Audit & Logs**            | `platform_audit_log`, `tenant_activity`                                        | Platform-wide audit trail                   |

### Tenant Databases (`humano_tenant_{identifier}`)

Each tenant gets a **dedicated, isolated database** containing:

| Entity Category     | Tables                                                                                                                             | Purpose                                        |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| **User Management** | `user`, `authority`, `user_authority`, `permission`, `persistent_token`                                                            | Tenant-specific authentication & authorization |
| **HR Management**   | `employee`, `department`, `position`, `organizational_unit`, `leave_request`, `attendance`, `training`, `performance_review`, etc. | Core HR functionality                          |
| **Payroll**         | `payroll_run`, `payroll_period`, `payroll_result`, `payslip`, `compensation`, `deduction`, `bonus`, `tax_bracket`, etc.            | Payroll processing                             |

---

## Implementation Strategy

### Phase 1: Database Infrastructure

#### 1.1 Tenant Database Configuration Entity

First, update the `TenantStorageConfig` entity to store full database connection details:

```java
package com.humano.domain.tenant;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Stores database connection configuration for each tenant.
 * Supports databases on same server or different servers.
 */
@Entity
@Table(name = "tenant_database_config")
public class TenantDatabaseConfig extends AbstractAuditingEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, unique = true)
  private Tenant tenant;

  /**
   * Database server hostname or IP address.
   * Examples: "localhost", "db-server-1.humano.com", "192.168.1.100"
   */
  @NotBlank
  @Column(name = "db_host", nullable = false)
  private String dbHost;

  /**
   * Database server port.
   * Default: 3306 for MySQL, 5432 for PostgreSQL
   */
  @NotNull
  @Column(name = "db_port", nullable = false)
  private Integer dbPort = 3306;

  /**
   * Name of the tenant's database.
   * Convention: humano_tenant_{subdomain}
   */
  @NotBlank
  @Column(name = "db_name", nullable = false)
  private String dbName;

  /**
   * Database username for this tenant's database.
   */
  @NotBlank
  @Column(name = "db_username", nullable = false)
  private String dbUsername;

  /**
   * Encrypted database password.
   */
  @NotBlank
  @Column(name = "db_password", nullable = false)
  private String dbPassword;

  /**
   * Additional JDBC connection parameters.
   * Example: "useSSL=true&serverTimezone=UTC"
   */
  @Column(name = "connection_params")
  private String connectionParams;

  /**
   * Database server region/location for geographic distribution.
   * Examples: "us-east-1", "eu-west-1", "ap-southeast-1"
   */
  @Column(name = "region")
  private String region;

  /**
   * Maximum connection pool size for this tenant.
   */
  @Column(name = "max_pool_size")
  private Integer maxPoolSize = 10;

  /**
   * Whether this tenant has a dedicated database server.
   */
  @Column(name = "dedicated_server")
  private boolean dedicatedServer = false;

  /**
   * Server cluster/group identifier for load balancing.
   */
  @Column(name = "server_group")
  private String serverGroup;

  // Getters and setters...

  /**
   * Builds the complete JDBC URL for this tenant's database.
   */
  public String buildJdbcUrl() {
    StringBuilder url = new StringBuilder();
    url.append("jdbc:mysql://").append(dbHost).append(":").append(dbPort).append("/").append(dbName);

    if (connectionParams != null && !connectionParams.isBlank()) {
      url.append("?").append(connectionParams);
    }

    return url.toString();
  }
  // ...existing getters/setters...
}

```

#### 1.2 Multi-DataSource Configuration

```java
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

@Configuration
public class MultiTenantDataSourceConfig {

  /**
   * Master data source for tenant/billing management.
   * This connects to the centralized humano_master_db database.
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

```

#### 1.3 Tenant Context Holder

```java
package com.humano.config.multitenancy;

/**
 * Thread-local storage for current tenant context.
 * Used to determine which database to route operations to.
 */
public class TenantContext {

  private static final ThreadLocal<String> CURRENT_TENANT = new InheritableThreadLocal<>();

  public static void setCurrentTenant(String tenantId) {
    CURRENT_TENANT.set(tenantId);
  }

  public static String getCurrentTenant() {
    return CURRENT_TENANT.get();
  }

  public static void clear() {
    CURRENT_TENANT.remove();
  }

  public static boolean isMasterContext() {
    String tenant = CURRENT_TENANT.get();
    return tenant == null || "master".equals(tenant);
  }
}

```

#### 1.4 Tenant Routing DataSource

```java
package com.humano.config.multitenancy;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Custom routing data source that dynamically selects the appropriate
 * tenant database based on the current tenant context.
 *
 * Supports routing to databases on different servers.
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
    return tenantDataSourceProvider.getOrCreateDataSource(tenantId);
  }

  public void setTenantDataSourceProvider(TenantDataSourceProvider provider) {
    this.tenantDataSourceProvider = provider;
  }
}

```

#### 1.5 Tenant DataSource Provider (Database-per-Tenant)

```java
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

  private String decryptPassword(String encryptedPassword) {
    // Implement password decryption logic
    // For production, use Jasypt or similar encryption library
    return encryptedPassword;
  }

  /**
   * Returns statistics about active tenant connections.
   */
  public Map<String, ConnectionPoolStats> getPoolStats() {
    Map<String, ConnectionPoolStats> stats = new ConcurrentHashMap<>();
    tenantDataSources.forEach((tenantId, ds) -> {
      stats.put(
        tenantId,
        new ConnectionPoolStats(
          ds.getHikariPoolMXBean().getActiveConnections(),
          ds.getHikariPoolMXBean().getIdleConnections(),
          ds.getHikariPoolMXBean().getTotalConnections(),
          ds.getHikariPoolMXBean().getThreadsAwaitingConnection()
        )
      );
    });
    return stats;
  }

  public record ConnectionPoolStats(int activeConnections, int idleConnections, int totalConnections, int threadsAwaiting) {}
}

```

#### 1.6 Multi-Tenant Properties

```java
package com.humano.config.multitenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for multi-tenant database setup.
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

  // Default connection parameters
  private String defaultConnectionParams =
    "useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";

  // Default database server for new tenants
  private String defaultDbHost = "localhost";
  private int defaultDbPort = 3306;

  // Getters and setters...

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

  public void setTenantDatabasePrefix(String prefix) {
    this.tenantDatabasePrefix = prefix;
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

  public void setDefaultMaxPoolSize(int size) {
    this.defaultMaxPoolSize = size;
  }

  public int getDefaultMinIdle() {
    return defaultMinIdle;
  }

  public void setDefaultMinIdle(int minIdle) {
    this.defaultMinIdle = minIdle;
  }

  public long getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(long timeout) {
    this.connectionTimeout = timeout;
  }

  public long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(long timeout) {
    this.idleTimeout = timeout;
  }

  public long getMaxLifetime() {
    return maxLifetime;
  }

  public void setMaxLifetime(long lifetime) {
    this.maxLifetime = lifetime;
  }

  public String getDefaultConnectionParams() {
    return defaultConnectionParams;
  }

  public void setDefaultConnectionParams(String params) {
    this.defaultConnectionParams = params;
  }

  public String getDefaultDbHost() {
    return defaultDbHost;
  }

  public void setDefaultDbHost(String host) {
    this.defaultDbHost = host;
  }

  public int getDefaultDbPort() {
    return defaultDbPort;
  }

  public void setDefaultDbPort(int port) {
    this.defaultDbPort = port;
  }
}

```

---

### Phase 2: Tenant Resolution

#### 2.1 Tenant Resolution Filter

```java
package com.humano.config.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that resolves the current tenant from the incoming request
 * and sets it in the TenantContext for the duration of the request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantResolutionFilter extends OncePerRequestFilter {

  private final TenantResolver tenantResolver;

  public TenantResolutionFilter(TenantResolver tenantResolver) {
    this.tenantResolver = tenantResolver;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {
    try {
      String tenantId = tenantResolver.resolveTenant(request);
      TenantContext.setCurrentTenant(tenantId);
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // Skip tenant resolution for platform admin endpoints
    return (
      path.startsWith("/api/platform/") ||
      path.startsWith("/management/") ||
      path.startsWith("/api/tenant-registration") ||
      path.startsWith("/api/public/")
    );
  }
}

```

#### 2.2 Tenant Resolver Strategies

```java
package com.humano.config.multitenancy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the tenant identifier from incoming requests.
 * Supports multiple resolution strategies in order of priority:
 * 1. X-Tenant-ID header
 * 2. Subdomain extraction
 * 3. JWT token claim
 */
@Component
public class TenantResolver {

  private static final String TENANT_HEADER = "X-Tenant-ID";
  private static final String TENANT_CLAIM = "tenant_id";

  public String resolveTenant(HttpServletRequest request) {
    // Strategy 1: Check header (useful for API clients)
    String tenantFromHeader = request.getHeader(TENANT_HEADER);
    if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
      return tenantFromHeader;
    }

    // Strategy 2: Extract from subdomain
    String host = request.getServerName();
    String tenantFromSubdomain = extractSubdomain(host);
    if (tenantFromSubdomain != null) {
      return tenantFromSubdomain;
    }

    // Strategy 3: Extract from JWT (handled by security filter)
    // Falls back to master database for platform-level operations
    return "master";
  }

  private String extractSubdomain(String host) {
    // Expected format: {tenant}.humano.com or {tenant}.localhost
    String[] parts = host.split("\\.");
    if (parts.length >= 2 && !parts[0].equals("www") && !parts[0].equals("api")) {
      return parts[0];
    }
    return null;
  }
}

```

---

### Phase 3: Tenant Provisioning Service

#### 3.1 Tenant Provisioning Orchestrator

```java
package com.humano.service.multitenancy;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantDatabaseConfig;
import com.humano.dto.tenant.TenantRegistrationDTO;
import com.humano.repository.tenant.TenantRepository;
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
 */
@Service
public class TenantProvisioningService {

  private static final Logger LOG = LoggerFactory.getLogger(TenantProvisioningService.class);

  private final TenantRepository tenantRepository;
  private final TenantDatabaseManager databaseManager;
  private final TenantMigrationService migrationService;
  private final TenantInitializationService initializationService;
  private final DatabaseServerSelector serverSelector;
  private final MultiTenantProperties properties;

  public TenantProvisioningService(
    TenantRepository tenantRepository,
    TenantDatabaseManager databaseManager,
    TenantMigrationService migrationService,
    TenantInitializationService initializationService,
    DatabaseServerSelector serverSelector,
    MultiTenantProperties properties
  ) {
    this.tenantRepository = tenantRepository;
    this.databaseManager = databaseManager;
    this.migrationService = migrationService;
    this.initializationService = initializationService;
    this.serverSelector = serverSelector;
    this.properties = properties;
  }

  @Transactional
  public Tenant provisionTenant(TenantRegistrationDTO registrationDTO) {
    LOG.info("Starting provisioning for tenant: {}", registrationDTO.getSubdomain());

    // Step 1: Create tenant record in master database
    Tenant tenant = createTenantRecord(registrationDTO);

    try {
      // Step 2: Select database server (same server, different server, or dedicated)
      DatabaseServerInfo serverInfo = serverSelector.selectServer(registrationDTO);
      LOG.info("Selected database server for tenant {}: {}:{}", tenant.getSubdomain(), serverInfo.host(), serverInfo.port());

      // Step 3: Create database configuration
      TenantDatabaseConfig dbConfig = createDatabaseConfig(tenant, serverInfo);
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

  private Tenant createTenantRecord(TenantRegistrationDTO dto) {
    Tenant tenant = new Tenant();
    tenant.setName(dto.getCompanyName());
    tenant.setDomain(dto.getDomain());
    tenant.setSubdomain(dto.getSubdomain());
    tenant.setTimezone(dto.getTimezone());
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
    // In production, use a proper password generator
    return java.util.UUID.randomUUID().toString().replace("-", "");
  }

  private int determinePoolSize(Tenant tenant) {
    // Determine pool size based on subscription plan
    return switch (tenant.getSubscriptionPlan().getName().toLowerCase()) {
      case "enterprise" -> 50;
      case "professional" -> 20;
      case "starter" -> 10;
      default -> properties.getDefaultMaxPoolSize();
    };
  }
}

/**
 * Record to hold database server information.
 */
record DatabaseServerInfo(String host, int port, String region, String serverGroup, boolean dedicated) {}

```

#### 3.2 Database Server Selector

```java
package com.humano.service.multitenancy;

import com.humano.dto.tenant.TenantRegistrationDTO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Selects the appropriate database server for a new tenant.
 * Supports different strategies:
 * - Same server (all tenants on default server)
 * - Round-robin across multiple servers
 * - Region-based selection
 * - Dedicated server for enterprise tenants
 */
@Component
public class DatabaseServerSelector {

  private final MultiTenantProperties properties;
  private final List<DatabaseServerConfig> availableServers;
  private final Map<String, AtomicInteger> serverLoadCounters = new ConcurrentHashMap<>();

  public DatabaseServerSelector(MultiTenantProperties properties) {
    this.properties = properties;
    this.availableServers = loadServerConfigurations();
  }

  /**
   * Selects the best database server for a new tenant.
   */
  public DatabaseServerInfo selectServer(TenantRegistrationDTO registration) {
    // Strategy 1: Dedicated server for enterprise plans
    if (requiresDedicatedServer(registration)) {
      return provisionDedicatedServer(registration);
    }

    // Strategy 2: Region-based selection (if region specified)
    if (registration.getPreferredRegion() != null) {
      DatabaseServerConfig regionalServer = findServerByRegion(registration.getPreferredRegion());
      if (regionalServer != null) {
        return toServerInfo(regionalServer, false);
      }
    }

    // Strategy 3: Round-robin across available servers
    DatabaseServerConfig selectedServer = selectByRoundRobin();
    return toServerInfo(selectedServer, false);
  }

  private boolean requiresDedicatedServer(TenantRegistrationDTO registration) {
    return (
      registration.getSubscriptionPlan() != null &&
      "enterprise".equalsIgnoreCase(registration.getSubscriptionPlan().getName()) &&
      registration.isRequestDedicatedServer()
    );
  }

  private DatabaseServerInfo provisionDedicatedServer(TenantRegistrationDTO registration) {
    // In a real implementation, this would provision a new database server
    // through cloud provider APIs (AWS RDS, GCP Cloud SQL, etc.)
    return new DatabaseServerInfo(
      "dedicated-" + registration.getSubdomain() + ".db.humano.com",
      3306,
      registration.getPreferredRegion(),
      "dedicated",
      true
    );
  }

  private DatabaseServerConfig findServerByRegion(String region) {
    return availableServers.stream().filter(server -> region.equals(server.region())).findFirst().orElse(null);
  }

  private DatabaseServerConfig selectByRoundRobin() {
    // Simple round-robin selection
    int index = serverLoadCounters.computeIfAbsent("round-robin", k -> new AtomicInteger(0)).getAndIncrement() % availableServers.size();
    return availableServers.get(index);
  }

  private DatabaseServerInfo toServerInfo(DatabaseServerConfig config, boolean dedicated) {
    return new DatabaseServerInfo(config.host(), config.port(), config.region(), config.serverGroup(), dedicated);
  }

  private List<DatabaseServerConfig> loadServerConfigurations() {
    // In production, load from configuration or database
    return List.of(
      new DatabaseServerConfig(properties.getDefaultDbHost(), properties.getDefaultDbPort(), "default", "primary")
      // Add more servers as needed:
      // new DatabaseServerConfig("db-server-2.humano.com", 3306, "us-east-1", "group-a"),
      // new DatabaseServerConfig("db-server-3.humano.com", 3306, "eu-west-1", "group-b"),
    );
  }

  record DatabaseServerConfig(String host, int port, String region, String serverGroup) {}
}

```

#### 3.3 Tenant Database Manager

```java
package com.humano.service.multitenancy;

import com.humano.domain.tenant.TenantDatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/**
 * Manages the lifecycle of tenant databases.
 * Creates and drops databases on specified database servers.
 */
@Service
public class TenantDatabaseManager {

  private static final Logger LOG = LoggerFactory.getLogger(TenantDatabaseManager.class);

  private final MultiTenantProperties properties;

  public TenantDatabaseManager(MultiTenantProperties properties) {
    this.properties = properties;
  }

  /**
   * Creates a new database for the tenant on the specified server.
   */
  public void createDatabase(TenantDatabaseConfig dbConfig) {
    LOG.info("Creating database {} on server {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());

    JdbcTemplate jdbcTemplate = createAdminJdbcTemplate(dbConfig);

    // Create the database
    String createDbSql = String.format(
      "CREATE DATABASE IF NOT EXISTS `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
      dbConfig.getDbName()
    );
    jdbcTemplate.execute(createDbSql);

    // Create dedicated user for this tenant's database
    createDatabaseUser(jdbcTemplate, dbConfig);

    LOG.info("Successfully created database {} on {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());
  }

  /**
   * Drops a tenant's database if it exists.
   */
  public void dropDatabaseIfExists(TenantDatabaseConfig dbConfig) {
    LOG.warn("Dropping database {} on server {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());

    JdbcTemplate jdbcTemplate = createAdminJdbcTemplate(dbConfig);

    // Drop the user first
    try {
      String dropUserSql = String.format("DROP USER IF EXISTS '%s'@'%%'", dbConfig.getDbUsername());
      jdbcTemplate.execute(dropUserSql);
    } catch (Exception e) {
      LOG.warn("Could not drop user {}: {}", dbConfig.getDbUsername(), e.getMessage());
    }

    // Drop the database
    String dropDbSql = String.format("DROP DATABASE IF EXISTS `%s`", dbConfig.getDbName());
    jdbcTemplate.execute(dropDbSql);

    LOG.info("Successfully dropped database {}", dbConfig.getDbName());
  }

  /**
   * Creates a dedicated database user for the tenant with limited permissions.
   */
  private void createDatabaseUser(JdbcTemplate jdbcTemplate, TenantDatabaseConfig dbConfig) {
    String username = dbConfig.getDbUsername();
    String password = dbConfig.getDbPassword();
    String database = dbConfig.getDbName();

    // Create user
    String createUserSql = String.format("CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s'", username, password);
    jdbcTemplate.execute(createUserSql);

    // Grant permissions only to this tenant's database
    String grantSql = String.format("GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'%%'", database, username);
    jdbcTemplate.execute(grantSql);

    // Apply the privileges
    jdbcTemplate.execute("FLUSH PRIVILEGES");

    LOG.info("Created database user {} with access to {}", username, database);
  }

  /**
   * Creates a JdbcTemplate with admin credentials to manage databases.
   */
  private JdbcTemplate createAdminJdbcTemplate(TenantDatabaseConfig dbConfig) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(properties.getDriverClassName());

    // Connect to the server without specifying a database
    String jdbcUrl = String.format(
      "jdbc:mysql://%s:%d?%s",
      dbConfig.getDbHost(),
      dbConfig.getDbPort(),
      "useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    );
    dataSource.setUrl(jdbcUrl);

    // Use admin credentials (from environment/config)
    dataSource.setUsername(System.getenv("DB_ADMIN_USERNAME"));
    dataSource.setPassword(System.getenv("DB_ADMIN_PASSWORD"));

    return new JdbcTemplate(dataSource);
  }

  /**
   * Checks if a database exists on the specified server.
   */
  public boolean databaseExists(TenantDatabaseConfig dbConfig) {
    JdbcTemplate jdbcTemplate = createAdminJdbcTemplate(dbConfig);
    String checkSql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
    return !jdbcTemplate.queryForList(checkSql, dbConfig.getDbName()).isEmpty();
  }
}

```

#### 3.4 Tenant Migration Service

```java
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
   */
  public void runMigrationsForAllTenants(Iterable<Tenant> tenants) {
    for (Tenant tenant : tenants) {
      try {
        runMigrations(tenant);
      } catch (Exception e) {
        LOG.error("Failed to run migrations for tenant {}: {}", tenant.getSubdomain(), e.getMessage());
      }
    }
  }
}

```

---

### Phase 4: Entity Organization

#### 4.1 Master Database Entities

Create a marker annotation for master database entities:

```java
package com.humano.config.multitenancy;

import java.lang.annotation.*;

/**
 * Marker annotation for entities that belong to the master database.
 * These entities are shared across all tenants (billing, subscriptions, etc.)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MasterDatabaseEntity {
}

```

#### 4.2 Tenant Database Entities

```java
package com.humano.config.multitenancy;

import java.lang.annotation.*;

/**
 * Marker annotation for entities that belong to tenant-specific databases.
 * Each tenant has their own isolated copy of these tables in their own database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantDatabaseEntity {
}

```

#### 4.3 JPA Configuration for Multiple Databases

```java
package com.humano.config.multitenancy;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MultiTenantJpaConfig {

  /**
   * Entity Manager Factory for MASTER database entities.
   */
  @Primary
  @Bean(name = "masterEntityManagerFactory")
  public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(@Qualifier("masterDataSource") DataSource dataSource) {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataSource);
    em.setPackagesToScan("com.humano.domain.tenant", "com.humano.domain.billing");
    em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    em.setPersistenceUnitName("master");
    em.setJpaPropertyMap(jpaProperties());
    return em;
  }

  /**
   * Entity Manager Factory for TENANT database entities.
   */
  @Bean(name = "tenantEntityManagerFactory")
  public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(@Qualifier("tenantDataSource") DataSource dataSource) {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataSource);
    em.setPackagesToScan(
      "com.humano.domain", // User, Authority
      "com.humano.domain.hr", // HR entities
      "com.humano.domain.payroll" // Payroll entities
    );
    em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    em.setPersistenceUnitName("tenant");
    em.setJpaPropertyMap(jpaProperties());
    return em;
  }

  @Primary
  @Bean(name = "masterTransactionManager")
  public PlatformTransactionManager masterTransactionManager(
    @Qualifier("masterEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf
  ) {
    return new JpaTransactionManager(emf.getObject());
  }

  @Bean(name = "tenantTransactionManager")
  public PlatformTransactionManager tenantTransactionManager(
    @Qualifier("tenantEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf
  ) {
    return new JpaTransactionManager(emf.getObject());
  }

  private Map<String, Object> jpaProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
    props.put("hibernate.hbm2ddl.auto", "none");
    props.put("hibernate.show_sql", false);
    props.put("hibernate.format_sql", true);
    return props;
  }
}

/**
 * Repository configuration for master database entities.
 */
@Configuration
@EnableJpaRepositories(
  basePackages = { "com.humano.repository.tenant", "com.humano.repository.billing" },
  entityManagerFactoryRef = "masterEntityManagerFactory",
  transactionManagerRef = "masterTransactionManager"
)
class MasterRepositoryConfig {}

/**
 * Repository configuration for tenant database entities.
 */
@Configuration
@EnableJpaRepositories(
  basePackages = { "com.humano.repository.user", "com.humano.repository.hr", "com.humano.repository.payroll" },
  entityManagerFactoryRef = "tenantEntityManagerFactory",
  transactionManagerRef = "tenantTransactionManager"
)
class TenantRepositoryConfig {}

```

---

### Phase 5: Liquibase Configuration

#### 5.1 Directory Structure

```
src/main/resources/config/liquibase/
├── master.xml                          # Master database changelog
├── master/
│   ├── 00000000000000_initial_schema.xml
│   ├── 20240101000001_tenant_tables.xml
│   ├── 20240101000002_billing_tables.xml
│   ├── 20240101000003_subscription_tables.xml
│   └── 20240101000004_tenant_db_config.xml
├── tenant/
│   ├── master.xml                      # Tenant database changelog
│   ├── 00000000000000_initial_schema.xml
│   ├── 20240101000001_user_tables.xml
│   ├── 20240101000002_hr_tables.xml
│   └── 20240101000003_payroll_tables.xml
└── data/
    ├── master/
    │   ├── authority.csv
    │   └── default_plans.csv
    └── tenant/
        ├── default_authorities.csv
        └── default_permissions.csv
```

#### 5.2 Master Database Changelog

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <property name="now" value="current_timestamp" dbms="mysql"/>
    <property name="uuidType" value="varchar(36)"/>

    <!-- Master database tables -->
    <include file="config/liquibase/master/00000000000000_initial_schema.xml"/>
    <include file="config/liquibase/master/20240101000001_tenant_tables.xml"/>
    <include file="config/liquibase/master/20240101000002_billing_tables.xml"/>
    <include file="config/liquibase/master/20240101000003_subscription_tables.xml"/>
    <include file="config/liquibase/master/20240101000004_tenant_db_config.xml"/>

</databaseChangeLog>
```

#### 5.3 Tenant Database Changelog

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <property name="now" value="current_timestamp" dbms="mysql"/>
    <property name="uuidType" value="varchar(36)"/>

    <!-- Tenant-specific database tables -->
    <include file="config/liquibase/tenant/00000000000000_initial_schema.xml"/>
    <include file="config/liquibase/tenant/20240101000001_user_tables.xml"/>
    <include file="config/liquibase/tenant/20240101000002_hr_tables.xml"/>
    <include file="config/liquibase/tenant/20240101000003_payroll_tables.xml"/>

</databaseChangeLog>
```

#### 5.4 Tenant Database Config Table (Master DB)

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20240101000004-1" author="humano">
        <createTable tableName="tenant_database_config">
            <column name="id" type="varchar(36)">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="tenant_id" type="varchar(36)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="db_host" type="varchar(255)">
                <constraints nullable="false"/>
            </column>
            <column name="db_port" type="int">
                <constraints nullable="false"/>
            </column>
            <column name="db_name" type="varchar(100)">
                <constraints nullable="false"/>
            </column>
            <column name="db_username" type="varchar(100)">
                <constraints nullable="false"/>
            </column>
            <column name="db_password" type="varchar(255)">
                <constraints nullable="false"/>
            </column>
            <column name="connection_params" type="varchar(500)"/>
            <column name="region" type="varchar(50)"/>
            <column name="max_pool_size" type="int" defaultValueNumeric="10"/>
            <column name="dedicated_server" type="boolean" defaultValueBoolean="false"/>
            <column name="server_group" type="varchar(50)"/>
            <column name="created_by" type="varchar(50)"/>
            <column name="created_date" type="timestamp"/>
            <column name="last_modified_by" type="varchar(50)"/>
            <column name="last_modified_date" type="timestamp"/>
        </createTable>

        <addForeignKeyConstraint
            baseTableName="tenant_database_config"
            baseColumnNames="tenant_id"
            referencedTableName="tenant"
            referencedColumnNames="id"
            constraintName="fk_tenant_db_config_tenant"/>
    </changeSet>

</databaseChangeLog>
```

---

### Phase 6: Application Configuration

#### 6.1 application.yml

```yaml
# Multi-tenant configuration
humano:
  multitenancy:
    enabled: true
    master-database: humano_master_db
    tenant-database-prefix: humano_tenant_
    driver-class-name: com.mysql.cj.jdbc.Driver

    # Default database server for new tenants
    default-db-host: ${DEFAULT_DB_HOST:localhost}
    default-db-port: ${DEFAULT_DB_PORT:3306}

    # Connection pool settings per tenant database
    default-max-pool-size: 10
    default-min-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000

    # Default connection parameters
    default-connection-params: 'useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8'

spring:
  datasource:
    master:
      url: jdbc:mysql://${MASTER_DB_HOST:localhost}:${MASTER_DB_PORT:3306}/humano_master_db?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC
      username: ${MASTER_DB_USERNAME:humano_master}
      password: ${MASTER_DB_PASSWORD:}
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        pool-name: HikariPool-Master
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true

  liquibase:
    change-log: classpath:config/liquibase/tenant.xml

# Database admin credentials (for creating tenant databases)
db:
  admin:
    username: ${DB_ADMIN_USERNAME:root}
    password: ${DB_ADMIN_PASSWORD:}
```

#### 6.2 application-prod.yml (Production Overrides)

```yaml
humano:
  multitenancy:
    # Production database servers
    default-db-host: ${PROD_DB_HOST:db-primary.humano.com}
    default-db-port: 3306

    # Higher pool sizes for production
    default-max-pool-size: 20
    default-min-idle: 5

spring:
  datasource:
    master:
      url: jdbc:mysql://${MASTER_DB_HOST:db-master.humano.com}:3306/humano_master_db?useSSL=true&requireSSL=true&serverTimezone=UTC
      hikari:
        maximum-pool-size: 30
        minimum-idle: 10

# Multiple database servers configuration
db:
  servers:
    - name: primary
      host: db-server-1.humano.com
      port: 3306
      region: us-east-1
      group: primary
    - name: secondary
      host: db-server-2.humano.com
      port: 3306
      region: eu-west-1
      group: secondary
    - name: asia
      host: db-server-3.humano.com
      port: 3306
      region: ap-southeast-1
      group: asia
```

---

## API Design

### Platform Admin Endpoints (Master Database)

```
POST   /api/platform/tenants                    # Create new tenant (provisions new database)
GET    /api/platform/tenants                    # List all tenants
GET    /api/platform/tenants/{id}               # Get tenant details
PUT    /api/platform/tenants/{id}               # Update tenant
DELETE /api/platform/tenants/{id}               # Deactivate tenant
POST   /api/platform/tenants/{id}/suspend       # Suspend tenant
POST   /api/platform/tenants/{id}/activate      # Activate tenant
POST   /api/platform/tenants/{id}/migrate       # Migrate tenant to different server
GET    /api/platform/tenants/{id}/db-stats      # Get tenant database statistics
GET    /api/platform/billing/invoices           # List all invoices
GET    /api/platform/billing/revenue            # Revenue analytics
GET    /api/platform/servers                    # List available database servers
GET    /api/platform/servers/{id}/tenants       # List tenants on a specific server
```

### Tenant-Scoped Endpoints (Tenant Database)

All existing HR/Payroll endpoints automatically use tenant context:

```
GET    /api/employees                           # Tenant's employees
POST   /api/employees                           # Create employee
GET    /api/departments                         # Tenant's departments
GET    /api/payroll/runs                        # Tenant's payroll runs
```

---

## Security Considerations

### 1. Complete Data Isolation

- Each tenant has a completely **separate database**
- Database-level isolation with **separate credentials per tenant**
- No possibility of cross-tenant data access at the database level
- Can place databases on **different physical servers** for enhanced isolation

### 2. Authentication Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│   Tenant    │────▶│   Lookup    │────▶│   Route to  │
│   Request   │     │  Resolution │     │   DB Config │     │  Tenant DB  │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                           │                    │                    │
                           ▼                    ▼                    ▼
                    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
                    │   Extract   │     │   Master    │     │   Tenant    │
                    │   Tenant ID │     │   Database  │     │   Database  │
                    └─────────────┘     └─────────────┘     └─────────────┘
```

### 3. JWT Token Structure

```json
{
  "sub": "user@tenant.com",
  "tenant_id": "acme-corp",
  "tenant_db": "humano_tenant_acme_corp",
  "tenant_db_host": "db-server-1.humano.com",
  "authorities": ["ROLE_ADMIN", "ROLE_HR"],
  "exp": 1735689600
}
```

### 4. Database Credential Security

- **Encrypted storage** of tenant database passwords
- **Separate credentials** per tenant database
- **Limited privileges** - tenant users can only access their database
- **Rotation support** - credentials can be rotated per tenant

---

## Deployment Architecture

### Single Server Setup (Development/Small Scale)

```
┌─────────────────────────────────────────────────────────────────┐
│                        Humano Application                        │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │     MySQL Server            │
                    │     (localhost:3306)        │
                    │  ┌───────────────────────┐  │
                    │  │  humano_master_db     │  │
                    │  └───────────────────────┘  │
                    │  ┌───────────────────────┐  │
                    │  │  humano_tenant_a      │  │
                    │  └───────────────────────┘  │
                    │  ┌───────────────────────┐  │
                    │  │  humano_tenant_b      │  │
                    │  └───────────────────────┘  │
                    │  ┌───────────────────────┐  │
                    │  │  humano_tenant_c      │  │
                    │  └───────────────────────┘  │
                    └─────────────────────────────┘
```

### Multi-Server Production Setup

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Load Balancer                                   │
│                          (nginx / AWS ALB)                                  │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
┌───────────────┐        ┌───────────────┐        ┌───────────────┐
│  Humano App   │        │  Humano App   │        │  Humano App   │
│  Instance 1   │        │  Instance 2   │        │  Instance N   │
└───────┬───────┘        └───────┬───────┘        └───────┬───────┘
        │                         │                         │
        └─────────────────────────┼─────────────────────────┘
                                  │
            ┌─────────────────────┼─────────────────────┐
            │                     │                     │
            ▼                     ▼                     ▼
┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐
│  DB Server: Master  │ │  DB Server 1        │ │  DB Server 2        │
│  (db-master.humano) │ │  (db-1.humano.com)  │ │  (db-2.humano.com)  │
│  ─────────────────  │ │  ─────────────────  │ │  ─────────────────  │
│  humano_master_db   │ │  humano_tenant_a    │ │  humano_tenant_d    │
│                     │ │  humano_tenant_b    │ │  humano_tenant_e    │
│                     │ │  humano_tenant_c    │ │  humano_tenant_f    │
└─────────────────────┘ └─────────────────────┘ └─────────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐
│  Master Replica     │ │  Server 1 Replica   │ │  Server 2 Replica   │
│  (Read Scaling)     │ │  (Read Scaling)     │ │  (Read Scaling)     │
└─────────────────────┘ └─────────────────────┘ └─────────────────────┘
```

### Geographic Distribution Setup

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Global Load Balancer                                 │
│                        (Route 53 / Cloudflare)                              │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
┌───────────────────┐    ┌───────────────────┐    ┌───────────────────┐
│   US-EAST-1       │    │   EU-WEST-1       │    │   AP-SOUTHEAST-1  │
│   ───────────     │    │   ───────────     │    │   ───────────     │
│   App Servers     │    │   App Servers     │    │   App Servers     │
│   DB: us-db.      │    │   DB: eu-db.      │    │   DB: ap-db.      │
│        humano.com │    │        humano.com │    │        humano.com │
│                   │    │                   │    │                   │
│   Tenants:        │    │   Tenants:        │    │   Tenants:        │
│   - US customers  │    │   - EU customers  │    │   - APAC customers│
│   - humano_t_usa1 │    │   - humano_t_eu1  │    │   - humano_t_sg1  │
│   - humano_t_usa2 │    │   - humano_t_eu2  │    │   - humano_t_jp1  │
└───────────────────┘    └───────────────────┘    └───────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │      MASTER DATABASE        │
                    │   (Centralized / Replicated)│
                    │      humano_master_db       │
                    └─────────────────────────────┘
```

---

## Tenant Migration Between Servers

### Migration Process

```java
/**
 * Service to migrate tenant databases between servers.
 */
@Service
public class TenantMigrationService {

  /**
   * Migrates a tenant's database to a different server.
   * Steps:
   * 1. Create new database on target server
   * 2. Export data from source database
   * 3. Import data to target database
   * 4. Update tenant database config
   * 5. Refresh connection pool
   * 6. Drop source database (optional)
   */
  @Transactional
  public void migrateTenant(String tenantId, DatabaseServerInfo targetServer) {
    // Implementation...
  }
}

```

### Migration API

```
POST /api/platform/tenants/{tenantId}/migrate
{
    "targetServer": {
        "host": "db-server-2.humano.com",
        "port": 3306,
        "region": "eu-west-1"
    },
    "scheduledTime": "2026-02-20T02:00:00Z",
    "keepSourceDatabase": false
}
```

---

## Monitoring & Observability

### Key Metrics

| Metric                                | Description                            |
| ------------------------------------- | -------------------------------------- |
| `humano.tenant.active_count`          | Number of active tenants               |
| `humano.tenant.db_count_per_server`   | Number of databases per server         |
| `humano.tenant.connection_pool_usage` | Connection pool utilization per tenant |
| `humano.tenant.request_count`         | Requests per tenant                    |
| `humano.tenant.db_size_bytes`         | Database size per tenant               |
| `humano.server.connection_count`      | Total connections per database server  |
| `humano.billing.mrr`                  | Monthly Recurring Revenue              |

### Logging Pattern

```
2026-02-17 10:30:45.123 [tenant=acme-corp] [db=humano_tenant_acme_corp] [server=db-1.humano.com] INFO  c.h.s.EmployeeService - Created employee: john.doe@acme.com
```

---

## Cost Optimization

### Database Server Allocation Guidelines

| Tenant Size  | Employees | Database Server                        | Pool Size |
| ------------ | --------- | -------------------------------------- | --------- |
| Starter      | 1-50      | Shared server                          | 5-10      |
| Professional | 51-500    | Shared server (preferred) or dedicated | 10-20     |
| Enterprise   | 500-5000  | Dedicated server                       | 20-50     |
| Enterprise+  | 5000+     | Dedicated cluster                      | 50-100    |

### Server Consolidation

- **Small tenants**: Multiple databases on shared servers
- **Medium tenants**: Dedicated databases on shared servers
- **Large tenants**: Dedicated server per tenant
- **Enterprise**: Dedicated cluster with read replicas

---

## Appendix A: Entity Classification

### Master Database Entities

```
com.humano.domain.tenant.Tenant
com.humano.domain.tenant.Organization
com.humano.domain.tenant.TenantDatabaseConfig
com.humano.domain.billing.Subscription
com.humano.domain.billing.SubscriptionPlan
com.humano.domain.billing.Feature
com.humano.domain.billing.Invoice
com.humano.domain.billing.Payment
com.humano.domain.billing.Coupon
com.humano.domain.shared.Country (reference data)
com.humano.domain.payroll.Currency (reference data)
```

### Tenant Database Entities

```
com.humano.domain.shared.User
com.humano.domain.shared.Authority
com.humano.domain.shared.Permission
com.humano.domain.shared.PersistentToken
com.humano.domain.hr.* (all HR entities)
com.humano.domain.payroll.* (all payroll entities)
```

---

## Appendix B: Implementation Checklist

### Infrastructure

- [ ] Create `TenantDatabaseConfig` entity
- [ ] Create `MultiTenantDataSourceConfig`
- [ ] Implement `TenantContext` and `TenantContextHolder`
- [ ] Create `TenantRoutingDataSource`
- [ ] Implement `TenantDataSourceProvider` with caching
- [ ] Create `MultiTenantProperties` configuration class

### Tenant Management

- [ ] Implement `TenantResolutionFilter`
- [ ] Create `TenantResolver` with multiple strategies
- [ ] Implement `DatabaseServerSelector`
- [ ] Create `TenantDatabaseManager`
- [ ] Implement `TenantProvisioningService`
- [ ] Implement `TenantMigrationService`

### Database & Migrations

- [ ] Reorganize Liquibase changelogs (master vs tenant)
- [ ] Create `tenant_database_config` table migration
- [ ] Create tenant database template migrations
- [ ] Update `application.yml` configuration

### API & Security

- [ ] Create platform admin endpoints
- [ ] Implement tenant-aware security
- [ ] Add JWT tenant claims
- [ ] Implement cross-tenant access prevention

### Operations

- [ ] Add monitoring and metrics
- [ ] Implement connection pool health checks
- [ ] Create tenant database backup automation
- [ ] Write migration scripts for existing data
- [ ] Create tenant onboarding documentation
- [ ] Performance testing with multiple servers

---

## Appendix C: Database Server Configuration Examples

### Docker Compose (Development)

```yaml
version: '3.8'
services:
  # Master database
  mysql-master:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: humano_master_db
    ports:
      - '3306:3306'
    volumes:
      - mysql-master-data:/var/lib/mysql

  # Tenant database server 1
  mysql-tenants-1:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
    ports:
      - '3307:3306'
    volumes:
      - mysql-tenants-1-data:/var/lib/mysql

  # Tenant database server 2 (simulating different server)
  mysql-tenants-2:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
    ports:
      - '3308:3306'
    volumes:
      - mysql-tenants-2-data:/var/lib/mysql

volumes:
  mysql-master-data:
  mysql-tenants-1-data:
  mysql-tenants-2-data:
```

### AWS RDS Configuration (Production)

```yaml
# terraform/rds.tf example
resource "aws_db_instance" "master" {
  identifier           = "humano-master"
  engine              = "mysql"
  engine_version      = "8.0"
  instance_class      = "db.r5.large"
  allocated_storage   = 100
  db_name             = "humano_master_db"
  multi_az            = true
  # ... other configurations
}

resource "aws_db_instance" "tenant_server_1" {
  identifier           = "humano-tenants-us-east"
  engine              = "mysql"
  engine_version      = "8.0"
  instance_class      = "db.r5.xlarge"
  allocated_storage   = 500
  multi_az            = true
  # ... other configurations
}
```

---

## References

- [Spring Multi-Tenancy Guide](https://docs.spring.io/spring-framework/reference/)
- [Hibernate Multi-Tenancy](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#multitenacy)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP)
- [Liquibase Multi-Database](https://docs.liquibase.com/)
- [AWS RDS Multi-Tenant Best Practices](https://aws.amazon.com/blogs/database/)

---

**Document Version:** 2.0  
**Last Updated:** February 17, 2026  
**Author:** Humano Development Team
