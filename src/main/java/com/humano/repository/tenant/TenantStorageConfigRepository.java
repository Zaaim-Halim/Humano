package com.humano.repository.tenant;

import com.humano.domain.tenant.Tenant;
import com.humano.domain.tenant.TenantStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the TenantStorageConfig entity.
 */
@Repository
public interface TenantStorageConfigRepository extends JpaRepository<TenantStorageConfig, UUID> {

    /**
     * Find the active storage configuration for a specific tenant.
     *
     * @param tenant the tenant
     * @return the active storage configuration if exists
     */
    Optional<TenantStorageConfig> findByTenantAndActiveTrue(Tenant tenant);

    /**
     * Find the active storage configuration for a tenant by its ID.
     *
     * @param tenantId the tenant ID
     * @return the active storage configuration if exists
     */
    Optional<TenantStorageConfig> findByTenant_IdAndActiveTrue(UUID tenantId);
}
