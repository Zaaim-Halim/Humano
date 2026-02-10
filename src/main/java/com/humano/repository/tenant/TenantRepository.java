package com.humano.repository.tenant;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Tenant} entity.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {
    Optional<Tenant> findByCode(String code);
    boolean existsByCode(String code);

    Optional<Tenant> findBySubdomain(String subdomain);
    Optional<Tenant> findByDomain(String domain);
    boolean existsByDomain(String domain);
    boolean existsBySubdomain(String subdomain);

    List<Tenant> findByStatus(TenantStatus status);

    long countByStatus(TenantStatus status);
}
