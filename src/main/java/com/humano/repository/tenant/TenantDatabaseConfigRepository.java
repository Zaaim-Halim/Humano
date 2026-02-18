package com.humano.repository.tenant;

import com.humano.domain.tenant.TenantDatabaseConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link TenantDatabaseConfig} entity.
 *
 * @author Humano Team
 */
@Repository
public interface TenantDatabaseConfigRepository extends JpaRepository<TenantDatabaseConfig, UUID> {
    /**
     * Find database configuration by tenant ID.
     *
     * @param tenantId the tenant ID
     * @return the database configuration if found
     */
    Optional<TenantDatabaseConfig> findByTenantId(UUID tenantId);

    /**
     * Find database configuration by database name.
     *
     * @param dbName the database name
     * @return the database configuration if found
     */
    Optional<TenantDatabaseConfig> findByDbName(String dbName);

    /**
     * Check if a database name already exists.
     *
     * @param dbName the database name
     * @return true if exists
     */
    boolean existsByDbName(String dbName);

    /**
     * Delete database configuration by tenant ID.
     *
     * @param tenantId the tenant ID
     */
    void deleteByTenantId(UUID tenantId);
}
