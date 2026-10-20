package com.humano.service.billing.entitlement;

import com.humano.domain.billing.Feature;
import com.humano.domain.billing.PlanFeature;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.FeatureRepository;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.PlanLimitException;
import com.humano.tenancy.MultiTenantProperties;
import com.humano.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the current workspace's plan allows. The plan is the subscription's (whatever its status — a
 * lapsed trial is handled by the read-only guard), else the plan the workspace was set up with (a
 * demo shows its showcase plan); the platform itself and a workspace with no plan are not limited.
 * <p>
 * Resolved from the master database and cached briefly per workspace; callers that change a plan or
 * what a plan includes evict it. Usage counts live in each workspace's own database, so the counting
 * stays with the caller: {@link #checkOneMore} only compares.
 */
@Service
public class EntitlementService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Entry(Entitlements entitlements, Instant expires) {}

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FeatureRepository featureRepository;
    private final SubscriptionAddonRepository addonRepository;
    private final MultiTenantProperties multiTenantProperties;
    private final TransactionTemplate masterTx;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public EntitlementService(
        TenantRepository tenantRepository,
        SubscriptionRepository subscriptionRepository,
        FeatureRepository featureRepository,
        SubscriptionAddonRepository addonRepository,
        MultiTenantProperties multiTenantProperties,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.featureRepository = featureRepository;
        this.addonRepository = addonRepository;
        this.multiTenantProperties = multiTenantProperties;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.masterTx.setReadOnly(true);
    }

    /** The entitlements of the workspace this request runs in. */
    public Entitlements current() {
        return forSubdomain(TenantContext.getCurrentTenant());
    }

    public Entitlements forSubdomain(String subdomain) {
        if (
            subdomain == null ||
            TenantContext.MASTER.equals(subdomain) ||
            subdomain.equalsIgnoreCase(multiTenantProperties.getPlatformTenant())
        ) {
            return Entitlements.UNLIMITED;
        }
        Entry entry = cache.get(subdomain);
        if (entry == null || entry.expires().isBefore(Instant.now())) {
            entry = new Entry(resolve(subdomain), Instant.now().plus(TTL));
            cache.put(subdomain, entry);
        }
        return entry.entitlements();
    }

    /** Refuses with {@link PlanLimitException} unless the plan includes {@code code}. */
    public void checkIncluded(String code) {
        Entitlements e = current();
        if (!e.includes(code)) {
            throw PlanLimitException.notInPlan(code, e.nameOf(code), e.planName());
        }
    }

    /** Refuses with {@link PlanLimitException} unless one more fits under the plan's limit for {@code code}. */
    public void checkOneMore(String code, long used) {
        Entitlements e = current();
        if (!e.includes(code)) {
            throw PlanLimitException.notInPlan(code, e.nameOf(code), e.planName());
        }
        if (!e.allowsOneMore(code, used)) {
            throw PlanLimitException.limitReached(code, e.limit(code), e.unitOf(code), e.planName());
        }
    }

    /**
     * Refuses with {@link PlanLimitException} when {@code usedAfter} (in the plan's unit for {@code code})
     * would exceed the plan's limit — for limits that are amounts, not counts (storage in GB).
     */
    public void checkWithin(String code, double usedAfter) {
        Entitlements e = current();
        if (!e.includes(code)) {
            throw PlanLimitException.notInPlan(code, e.nameOf(code), e.planName());
        }
        Integer limit = e.catalog().containsKey(code) ? e.limit(code) : null;
        if (limit != null && usedAfter > limit) {
            throw PlanLimitException.limitReached(code, limit, e.unitOf(code), e.planName());
        }
    }

    /** Forget a workspace's entitlements (its plan changed) — now, and again once the change is committed. */
    public void evict(String subdomain) {
        if (subdomain != null) {
            nowAndAfterCommit(() -> cache.remove(subdomain));
        }
    }

    /** Forget every workspace's entitlements (what a plan includes changed). */
    public void evictAll() {
        nowAndAfterCommit(cache::clear);
    }

    /** A request reading before the commit could cache the old plan again: clear once more after it. */
    private static void nowAndAfterCommit(Runnable clear) {
        clear.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        clear.run();
                    }
                }
            );
        }
    }

    private Entitlements resolve(String subdomain) {
        Entitlements resolved = masterTx.execute(tx -> {
            Tenant tenant = tenantRepository.findBySubdomain(subdomain).orElse(null);
            if (tenant == null) {
                return Entitlements.UNLIMITED;
            }
            Subscription subscription = subscriptionRepository.findByTenantId(tenant.getId()).orElse(null);
            SubscriptionPlan plan = subscription != null ? subscription.getSubscriptionPlan() : tenant.getSubscriptionPlan();
            if (plan == null) {
                return Entitlements.UNLIMITED;
            }
            Map<String, Entitlements.Label> catalog = featureRepository
                .findAll()
                .stream()
                .filter(Feature::isActive)
                .collect(
                    Collectors.toUnmodifiableMap(
                        Feature::getCode,
                        f -> new Entitlements.Label(f.getId(), f.getName(), f.getUnit()),
                        (a, b) -> a
                    )
                );
            Map<String, Entitlements.Grant> grants = new HashMap<>();
            for (PlanFeature pf : plan.getFeatures()) {
                if (pf.getFeature().isActive()) {
                    grants.put(pf.getFeature().getCode(), new Entitlements.Grant(pf.isIncluded(), pf.getLimitValue()));
                }
            }
            // Packs bought on top of the plan raise its limits (e.g. extra storage).
            if (subscription != null) {
                for (SubscriptionAddon addon : addonRepository.findBySubscriptionId(subscription.getId())) {
                    grants.computeIfPresent(addon.getFeatureCode(), (code, g) ->
                        g.included() && g.limit() != null ? new Entitlements.Grant(true, g.limit() + addon.addedAmount()) : g
                    );
                }
            }
            String name = plan.getDisplayName() != null ? plan.getDisplayName() : String.valueOf(plan.getSubscriptionType());
            return new Entitlements(plan.getId(), name, catalog, Map.copyOf(grants));
        });
        return resolved != null ? resolved : Entitlements.UNLIMITED;
    }
}
