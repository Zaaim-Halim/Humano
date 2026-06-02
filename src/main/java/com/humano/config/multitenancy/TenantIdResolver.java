package com.humano.config.multitenancy;

import com.humano.domain.tenant.Tenant;
import com.humano.repository.tenant.TenantRepository;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a tenant {@code subdomain} (the canonical identifier in {@link TenantContext})
 * to the master-DB {@link Tenant#getId() UUID} primary key, with an in-memory cache to
 * avoid a master-DB lookup on every file/storage operation.
 *
 * <p>Belongs to the master persistence unit — all lookups go through
 * {@code masterTransactionManager} via the partitioned scan in
 * {@code MultiTenantJpaConfig$MasterRepositoryConfig}.
 */
@Component
public class TenantIdResolver {

    private final TenantRepository tenantRepository;
    private final ConcurrentMap<String, UUID> subdomainToId = new ConcurrentHashMap<>();

    public TenantIdResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Look up the UUID for a given subdomain. Throws {@link TenantNotFoundException} if no
     * tenant with that subdomain exists.
     */
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public UUID resolveId(String subdomain) {
        if (subdomain == null || subdomain.isBlank()) {
            throw new TenantNotFoundException("No subdomain provided");
        }
        UUID cached = subdomainToId.get(subdomain);
        if (cached != null) {
            return cached;
        }
        Tenant tenant = tenantRepository
            .findBySubdomain(subdomain)
            .orElseThrow(() -> new TenantNotFoundException("Unknown tenant subdomain: " + subdomain));
        subdomainToId.put(subdomain, tenant.getId());
        return tenant.getId();
    }

    /**
     * Resolve the UUID for the subdomain in the current {@link TenantContext}. Rejects the
     * reserved {@code master} context — it has no business UUID.
     */
    public UUID requireCurrentTenantId() {
        String subdomain = TenantContext.getCurrentTenant();
        if (subdomain == null || "master".equals(subdomain)) {
            throw new TenantNotFoundException("No tenant context set for this thread");
        }
        return resolveId(subdomain);
    }

    /** Evict a subdomain from the cache (call after tenant deprovision / subdomain rename). */
    public void invalidate(String subdomain) {
        if (subdomain != null) {
            subdomainToId.remove(subdomain);
        }
    }
}
