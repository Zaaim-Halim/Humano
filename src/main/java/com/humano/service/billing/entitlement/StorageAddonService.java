package com.humano.service.billing.entitlement;

import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.responses.AddonRowResponse;
import com.humano.dto.billing.responses.CustomerStorageResponse;
import com.humano.dto.billing.responses.StorageAddonResponse;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Extra storage bought on top of the plan ({@link StorageAddonOffer}): packs raise the plan's storage
 * limit right away and are billed on each renewal. Removing packs is refused while the workspace
 * stores more than the lower limit would allow; a plan that cannot have them drops them.
 */
@Service
public class StorageAddonService {

    private static final Logger log = LoggerFactory.getLogger(StorageAddonService.class);
    private static final String ENTITY_NAME = "storageAddon";

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionAddonRepository addonRepository;
    private final StorageAddonOffer offer;
    private final StorageQuotaService storageQuotaService;
    private final EntitlementService entitlementService;
    private final String currency;
    private final TransactionTemplate masterTx;

    public StorageAddonService(
        SubscriptionRepository subscriptionRepository,
        TenantRepository tenantRepository,
        SubscriptionAddonRepository addonRepository,
        StorageAddonOffer offer,
        StorageQuotaService storageQuotaService,
        EntitlementService entitlementService,
        @Value("${humano.billing.currency:USD}") String currency,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.addonRepository = addonRepository;
        this.offer = offer;
        this.storageQuotaService = storageQuotaService;
        this.entitlementService = entitlementService;
        this.currency = currency;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
    }

    public StorageAddonResponse view(UUID tenantId) {
        return masterTx.execute(tx -> {
            Subscription sub = subscriptionRepository.findByTenantId(tenantId).orElse(null);
            SubscriptionAddon addon = sub == null
                ? null
                : addonRepository.findBySubscriptionIdAndFeatureCode(sub.getId(), FeatureCodes.STORAGE).orElse(null);
            return toResponse(sub != null && offer.availableOn(sub.getSubscriptionPlan()), addon);
        });
    }

    /**
     * Self-service: sets the current workspace's packs (0 removes them). New packs are at today's
     * price; packs kept keep theirs.
     */
    public StorageAddonResponse setPacks(UUID tenantId, int packs) {
        // Measured before the master transaction: usage lives in the workspace's own database.
        double usedGb = storageQuotaService.usedBytes() / StorageQuotaService.BYTES_PER_GB;
        return apply(tenantId, packs, usedGb, null);
    }

    /**
     * Owner console: sets a customer's packs, optionally at a negotiated monthly price per pack
     * ({@code unitPrice}, applied to all their packs). Usage is measured in the customer's database.
     */
    public StorageAddonResponse setPacksAsPlatform(UUID tenantId, int packs, BigDecimal unitPrice) {
        Double usedGb = usedGbOf(tenantId);
        return apply(tenantId, packs, usedGb != null ? usedGb : 0, unitPrice);
    }

    /** Owner console: a customer's storage — the packs, the plan's limit with them, and what is used. */
    public CustomerStorageResponse customerStorage(UUID tenantId) {
        StorageAddonResponse addon = view(tenantId);
        String subdomain = subdomainOf(tenantId);
        Entitlements e = subdomain == null ? Entitlements.UNLIMITED : entitlementService.forSubdomain(subdomain);
        return new CustomerStorageResponse(e.planName(), addon, usedGbOf(tenantId), e.limit(FeatureCodes.STORAGE));
    }

    /** Owner console: every add-on held, newest first. */
    public List<AddonRowResponse> listAll() {
        return masterTx.execute(tx ->
            addonRepository
                .findAllWithSubscription()
                .stream()
                .map(a -> {
                    Subscription s = a.getSubscription();
                    SubscriptionPlan plan = s.getSubscriptionPlan();
                    return new AddonRowResponse(
                        a.getId(),
                        s.getTenant().getId(),
                        s.getTenant().getName(),
                        s.getTenant().getSubdomain(),
                        plan.getDisplayName() != null ? plan.getDisplayName() : String.valueOf(plan.getSubscriptionType()),
                        s.getBillingCycle(),
                        s.getStatus(),
                        a.getFeatureCode(),
                        a.getQuantity(),
                        a.getUnitSize(),
                        a.getUnitPrice(),
                        a.monthlyPrice(),
                        a.getBilledUntil(),
                        a.getCreatedDate()
                    );
                })
                .toList()
        );
    }

    private StorageAddonResponse apply(UUID tenantId, int packs, double usedGb, BigDecimal unitPrice) {
        StorageAddonResponse response = masterTx.execute(tx -> {
            Subscription sub = subscriptionRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> EntityNotFoundException.create("Subscription for Tenant", tenantId));
            if (!offer.availableOn(sub.getSubscriptionPlan())) {
                throw new BadRequestAlertException("Extra storage is not available on this plan", ENTITY_NAME, "addonunavailable");
            }
            SubscriptionAddon addon = addonRepository.findBySubscriptionIdAndFeatureCode(sub.getId(), FeatureCodes.STORAGE).orElse(null);
            int before = addon == null ? 0 : addon.getQuantity();
            if (packs < before) {
                Integer limit = entitlementService.forSubdomain(sub.getTenant().getSubdomain()).limit(FeatureCodes.STORAGE);
                int unit = addon.getUnitSize();
                if (limit != null && usedGb > limit - (long) (before - packs) * unit) {
                    throw new BadRequestAlertException(
                        "You store more than the lower limit allows; delete files first",
                        ENTITY_NAME,
                        "storageinuse"
                    );
                }
            }
            if (packs == 0) {
                if (addon != null) {
                    addonRepository.delete(addon);
                }
                addon = null;
            } else {
                if (addon == null) {
                    addon = new SubscriptionAddon();
                    addon.setSubscription(sub);
                    addon.setFeatureCode(FeatureCodes.STORAGE);
                    addon.setUnitSize(offer.packGb());
                    addon.setUnitPrice(unitPrice != null ? unitPrice : offer.monthlyPrice());
                    // Billed monthly from now on (yearly plans: own invoice; monthly plans: on the renewal).
                    addon.setBilledUntil(java.time.Instant.now());
                }
                addon.setQuantity(packs);
                if (unitPrice != null) {
                    addon.setUnitPrice(unitPrice);
                }
                addon = addonRepository.save(addon);
            }
            log.info("Tenant {} now has {} pack(s) of extra storage (was {})", sub.getTenant().getSubdomain(), packs, before);
            entitlementService.evict(sub.getTenant().getSubdomain());
            return toResponse(true, addon);
        });
        return response;
    }

    private String subdomainOf(UUID tenantId) {
        return masterTx.execute(tx -> tenantRepository.findById(tenantId).map(Tenant::getSubdomain).orElse(null));
    }

    /** GB stored by a customer, read in its own database; {@code null} when it cannot be read. */
    private Double usedGbOf(UUID tenantId) {
        String subdomain = subdomainOf(tenantId);
        if (subdomain == null) {
            return null;
        }
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(subdomain);
        try {
            return Math.round((storageQuotaService.usedBytes() / StorageQuotaService.BYTES_PER_GB) * 100) / 100d;
        } catch (RuntimeException e) {
            log.warn("Could not read the storage used by {}: {}", subdomain, e.getMessage());
            return null;
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private StorageAddonResponse toResponse(boolean available, SubscriptionAddon addon) {
        return new StorageAddonResponse(
            available,
            offer.packGb(),
            offer.monthlyPrice(),
            currency,
            addon == null ? 0 : addon.getQuantity(),
            addon == null ? null : addon.getUnitPrice(),
            addon == null ? BigDecimal.ZERO : addon.monthlyPrice(),
            offer.planTypes()
        );
    }
}
