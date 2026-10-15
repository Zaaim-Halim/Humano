package com.humano.repository.tenant;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Tenant} entity.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {
    Optional<Tenant> findBySubdomain(String subdomain);
    Optional<Tenant> findByDomain(String domain);
    boolean existsByDomain(String domain);
    boolean existsBySubdomain(String subdomain);

    List<Tenant> findByStatus(TenantStatus status);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    long countByStatus(TenantStatus status);

    /** Customers only — demo workspaces are not customers. */
    Page<Tenant> findByDemoFalse(Pageable pageable);

    Page<Tenant> findByStatusAndDemoFalse(TenantStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "subscriptionPlan")
    List<Tenant> findByDemoTrueOrderByCreatedDateDesc();

    /** Ready demo workspaces past their expiry, still open — the expiry job suspends them. */
    @Query("SELECT t FROM Tenant t WHERE t.demo = true AND t.status = 'ACTIVE' AND t.demoExpiresAt <= :now")
    List<Tenant> findExpiredDemos(@Param("now") Instant now);

    /**
     * Projection-only query for tenant fan-out tasks. Returns just the subdomain
     * strings so a 10k-tenant fleet doesn't pull full {@link Tenant} entities
     * (with logo, address, billing fields) into memory just to iterate. Tenants without a
     * database (deprovisioned, or never provisioned) have nothing to run against and are skipped.
     */
    @Query("SELECT t.subdomain FROM Tenant t WHERE t.status = :status AND t.databaseConfig IS NOT NULL ORDER BY t.subdomain")
    List<String> findSubdomainsByStatus(@Param("status") TenantStatus status);
}
