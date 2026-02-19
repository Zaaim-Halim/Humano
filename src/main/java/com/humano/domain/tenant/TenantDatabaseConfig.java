package com.humano.domain.tenant;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Stores database connection configuration for each tenant.
 * Supports databases on same server or different servers.
 *
 * @author Humano Team
 */
@Entity
@Table(name = "tenant_database_config")
public class TenantDatabaseConfig extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
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

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getDbHost() {
        return dbHost;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public Integer getDbPort() {
        return dbPort;
    }

    public void setDbPort(Integer dbPort) {
        this.dbPort = dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getConnectionParams() {
        return connectionParams;
    }

    public void setConnectionParams(String connectionParams) {
        this.connectionParams = connectionParams;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public boolean isDedicatedServer() {
        return dedicatedServer;
    }

    public void setDedicatedServer(boolean dedicatedServer) {
        this.dedicatedServer = dedicatedServer;
    }

    public String getServerGroup() {
        return serverGroup;
    }

    public void setServerGroup(String serverGroup) {
        this.serverGroup = serverGroup;
    }

    /**
     * Builds the complete JDBC URL for this tenant's database.
     *
     * @return the complete JDBC URL
     */
    public String buildJdbcUrl() {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://").append(dbHost).append(":").append(dbPort).append("/").append(dbName);

        if (connectionParams != null && !connectionParams.isBlank()) {
            url.append("?").append(connectionParams);
        }

        return url.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantDatabaseConfig that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "TenantDatabaseConfig{" +
            "id=" +
            id +
            ", dbHost='" +
            dbHost +
            '\'' +
            ", dbPort=" +
            dbPort +
            ", dbName='" +
            dbName +
            '\'' +
            ", region='" +
            region +
            '\'' +
            ", dedicatedServer=" +
            dedicatedServer +
            ", serverGroup='" +
            serverGroup +
            '\'' +
            '}'
        );
    }
}
