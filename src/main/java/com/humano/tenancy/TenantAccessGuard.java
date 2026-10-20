package com.humano.tenancy;

import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Whether a customer workspace may be used right now. A suspended, deactivated or deleted tenant
 * is locked out — including sessions that were already signed in. A workspace whose trial ended
 * without a plan (subscription {@code EXPIRED}) is read-only until it is suspended: people can sign
 * in and look, and an admin can pick a plan, but nothing else changes. Both are cached briefly to
 * keep the check off the hot path; the platform evicts a tenant's entry when it changes status.
 */
@Component
public class TenantAccessGuard {

    private static final Set<TenantStatus> LOCKED = EnumSet.of(TenantStatus.SUSPENDED, TenantStatus.DEACTIVATED, TenantStatus.DELETED);
    private static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(TenantStatus status, boolean readOnly, Instant expires) {}

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public TenantAccessGuard(TenantRepository tenantRepository, SubscriptionRepository subscriptionRepository) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** The tenant's status when it is locked, otherwise {@code null} (unknown tenants are left to the usual resolution). */
    public TenantStatus lockedStatus(String subdomain) {
        Entry entry = entry(subdomain);
        return entry.status() != null && LOCKED.contains(entry.status()) ? entry.status() : null;
    }

    /** Whether the workspace is open but read-only (its trial ended without a plan). */
    public boolean readOnly(String subdomain) {
        return entry(subdomain).readOnly();
    }

    /** Forget a tenant's cached status (call after suspending, activating or deprovisioning it, or when its subscription ends or restarts). */
    public void evict(String subdomain) {
        cache.remove(subdomain);
    }

    private Entry entry(String subdomain) {
        Entry entry = cache.get(subdomain);
        if (entry == null || entry.expires().isBefore(Instant.now())) {
            Optional<Tenant> tenant = tenantRepository.findBySubdomain(subdomain);
            TenantStatus status = tenant.map(Tenant::getStatus).orElse(null);
            boolean readOnly =
                status == TenantStatus.ACTIVE &&
                tenant
                    .flatMap(t -> subscriptionRepository.findByTenantId(t.getId()))
                    .map(s -> s.getStatus() == SubscriptionStatus.EXPIRED)
                    .orElse(false);
            entry = new Entry(status, readOnly, Instant.now().plus(TTL));
            cache.put(subdomain, entry);
        }
        return entry;
    }
}
