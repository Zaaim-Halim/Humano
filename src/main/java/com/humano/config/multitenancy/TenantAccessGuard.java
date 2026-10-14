package com.humano.config.multitenancy;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.repository.tenant.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Whether a customer workspace may be used right now. A suspended, deactivated or deleted tenant
 * is locked out — including sessions that were already signed in. Statuses are cached briefly to
 * keep the check off the hot path; the platform evicts a tenant's entry when it changes status.
 */
@Component
public class TenantAccessGuard {

    private static final Set<TenantStatus> LOCKED = EnumSet.of(TenantStatus.SUSPENDED, TenantStatus.DEACTIVATED, TenantStatus.DELETED);
    private static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(TenantStatus status, Instant expires) {}

    private final TenantRepository tenantRepository;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public TenantAccessGuard(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /** The tenant's status when it is locked, otherwise {@code null} (unknown tenants are left to the usual resolution). */
    public TenantStatus lockedStatus(String subdomain) {
        Entry entry = cache.get(subdomain);
        if (entry == null || entry.expires().isBefore(Instant.now())) {
            TenantStatus status = tenantRepository.findBySubdomain(subdomain).map(t -> t.getStatus()).orElse(null);
            entry = new Entry(status, Instant.now().plus(TTL));
            cache.put(subdomain, entry);
        }
        return entry.status() != null && LOCKED.contains(entry.status()) ? entry.status() : null;
    }

    /** Forget a tenant's cached status (call after suspending, activating or deprovisioning it). */
    public void evict(String subdomain) {
        cache.remove(subdomain);
    }
}
