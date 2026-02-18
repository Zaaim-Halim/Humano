package com.humano.service.multitenancy;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.dto.tenant.TenantRegistrationDTO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Selects the appropriate database server for a new tenant.
 * Supports different strategies:
 * - Same server (all tenants on default server)
 * - Round-robin across multiple servers
 * - Region-based selection
 * - Dedicated server for enterprise tenants
 *
 * @author Humano Team
 */
@Component
public class DatabaseServerSelector {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseServerSelector.class);

    private final MultiTenantProperties properties;
    private final List<DatabaseServerConfig> availableServers;
    private final Map<String, AtomicInteger> serverLoadCounters = new ConcurrentHashMap<>();

    public DatabaseServerSelector(MultiTenantProperties properties) {
        this.properties = properties;
        this.availableServers = loadServerConfigurations();
    }

    /**
     * Selects the best database server for a new tenant.
     *
     * @param registration the tenant registration details
     * @return the selected database server info
     */
    public DatabaseServerInfo selectServer(TenantRegistrationDTO registration) {
        // Strategy 1: Dedicated server for enterprise plans
        if (requiresDedicatedServer(registration)) {
            LOG.info("Provisioning dedicated server for enterprise tenant: {}", registration.getSubdomain());
            return provisionDedicatedServer(registration);
        }

        // Strategy 2: Region-based selection (if region specified)
        if (registration.getPreferredRegion() != null) {
            DatabaseServerConfig regionalServer = findServerByRegion(registration.getPreferredRegion());
            if (regionalServer != null) {
                LOG.info("Selected regional server {} for tenant: {}", regionalServer.host(), registration.getSubdomain());
                return toServerInfo(regionalServer);
            }
        }

        // Strategy 3: Round-robin across available servers
        DatabaseServerConfig selectedServer = selectByRoundRobin();
        LOG.info("Selected server {} (round-robin) for tenant: {}", selectedServer.host(), registration.getSubdomain());
        return toServerInfo(selectedServer);
    }

    private boolean requiresDedicatedServer(TenantRegistrationDTO registration) {
        return (
            registration.getSubscriptionPlan() != null &&
            registration.getSubscriptionPlan().getSubscriptionType() != null &&
            "ENTERPRISE".equalsIgnoreCase(registration.getSubscriptionPlan().getSubscriptionType().name()) &&
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
        int index =
            serverLoadCounters.computeIfAbsent("round-robin", k -> new AtomicInteger(0)).getAndIncrement() % availableServers.size();
        return availableServers.get(index);
    }

    private DatabaseServerInfo toServerInfo(DatabaseServerConfig config) {
        return new DatabaseServerInfo(config.host(), config.port(), config.region(), config.serverGroup(), false);
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

    /**
     * Record for database server configuration.
     */
    public record DatabaseServerConfig(String host, int port, String region, String serverGroup) {}
}
