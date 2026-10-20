package com.humano.service.billing;

import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionAddon;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.ChangeMyPlanRequest;
import com.humano.dto.billing.requests.CreateSubscriptionRequest;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.repository.billing.SubscriptionAddonRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.billing.entitlement.StorageAddonOffer;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.tenancy.TenantAccessGuard;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer managing its own subscription from its workspace: change plan or billing cycle
 * (upgrade or downgrade, effective now; the new price applies from the next invoice), cancel at
 * the end of the current period (or trial), take a scheduled cancellation back, or pick a plan
 * again once the subscription has ended — which starts a new period and bills it right away.
 * During a free trial, choosing a plan (even the trial's own) sets it to continue: the paid plan
 * starts when the trial ends ({@code autoRenew}), see {@link BillingLifecycleService}.
 * <p>
 * Demo workspaces have no subscription and cannot start one themselves — the platform team
 * converts them. Only plans on sale to everyone (active and publicly listed) can be chosen, besides
 * the customer's current plan (to switch billing cycle).
 */
@Service
@Transactional
public class SelfServiceBillingService {

    private static final Logger log = LoggerFactory.getLogger(SelfServiceBillingService.class);
    private static final String ENTITY_NAME = "subscription";
    /** Still running: the plan can change in place. */
    private static final Set<SubscriptionStatus> RUNNING = EnumSet.of(
        SubscriptionStatus.TRIAL,
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.PAST_DUE,
        SubscriptionStatus.PENDING_PAYMENT
    );

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final BillingLifecycleService lifecycleService;
    private final TenantAccessGuard accessGuard;
    private final EntitlementService entitlementService;
    private final SubscriptionAddonRepository addonRepository;
    private final StorageAddonOffer storageAddonOffer;

    public SelfServiceBillingService(
        TenantRepository tenantRepository,
        SubscriptionRepository subscriptionRepository,
        SubscriptionPlanRepository planRepository,
        SubscriptionService subscriptionService,
        BillingLifecycleService lifecycleService,
        TenantAccessGuard accessGuard,
        EntitlementService entitlementService,
        SubscriptionAddonRepository addonRepository,
        StorageAddonOffer storageAddonOffer
    ) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
        this.lifecycleService = lifecycleService;
        this.accessGuard = accessGuard;
        this.entitlementService = entitlementService;
        this.addonRepository = addonRepository;
        this.storageAddonOffer = storageAddonOffer;
    }

    public SubscriptionResponse changePlan(UUID tenantId, ChangeMyPlanRequest request) {
        Tenant tenant = customer(tenantId);
        Optional<Subscription> existing = subscriptionRepository.findByTenantId(tenantId);
        UUID currentPlanId = existing.map(sub -> sub.getSubscriptionPlan().getId()).orElse(null);
        // On sale to everyone (active and on the pricing page), or the plan the customer already has.
        SubscriptionPlan plan = planRepository
            .findById(request.planId())
            .filter(p -> (p.isActive() && p.isPublicListed()) || p.getId().equals(currentPlanId))
            .orElseThrow(() -> new BadRequestAlertException("This plan is not available", ENTITY_NAME, "planunavailable"));
        if (existing.isEmpty()) {
            tenant.setSubscriptionPlan(plan);
            tenantRepository.save(tenant);
            log.info("Tenant {} subscribed to {}", tenant.getSubdomain(), plan.getDisplayName());
            SubscriptionResponse created = subscriptionService.createSubscription(
                new CreateSubscriptionRequest(tenantId, plan.getId(), request.billingCycle(), true, null, null)
            );
            lifecycleService.issueFirstInvoice(created.id());
            return subscriptionService.getSubscriptionById(created.id());
        }
        Subscription sub = existing.orElseThrow();
        boolean restarted = !RUNNING.contains(sub.getStatus());
        boolean sameTerms = sub.getSubscriptionPlan().getId().equals(plan.getId()) && sub.getBillingCycle() == request.billingCycle();
        // During a trial that is not set to continue, choosing even the trial's own plan means "keep it after the trial".
        boolean keepsTrialPlan = sub.getStatus() == SubscriptionStatus.TRIAL && !Boolean.TRUE.equals(sub.getAutoRenew());
        if (RUNNING.contains(sub.getStatus())) {
            if (sameTerms && !Boolean.TRUE.equals(sub.getCancelAtPeriodEnd()) && !keepsTrialPlan) {
                throw new BadRequestAlertException("You are already on this plan", ENTITY_NAME, "sameplan");
            }
        } else {
            // Ended (cancelled, expired, suspended): starts again today, on the chosen plan.
            Instant now = Instant.now();
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setStartDate(now);
            sub.setEndDate(null);
            sub.setCurrentPeriodStart(now);
            sub.setCurrentPeriodEnd(now.plus(request.billingCycle() == BillingCycle.YEARLY ? 365 : 30, ChronoUnit.DAYS));
            sub.setTrialStart(null);
            sub.setTrialEnd(null);
            sub.setDunningAttempt(0);
        }
        boolean toYearly = sub.getBillingCycle() != BillingCycle.YEARLY && request.billingCycle() == BillingCycle.YEARLY;
        sub.setSubscriptionPlan(plan);
        sub.setBillingCycle(request.billingCycle());
        adjustAddons(sub, plan, toYearly);
        sub.setCancelAtPeriodEnd(false);
        sub.setAutoRenew(true);
        subscriptionRepository.save(sub);
        tenant.setSubscriptionPlan(plan);
        tenantRepository.save(tenant);
        entitlementService.evict(tenant.getSubdomain());
        if (restarted) {
            lifecycleService.issueFirstInvoice(sub.getId());
            // A trial that had ended left the workspace read-only; it is open again now.
            accessGuard.evict(tenant.getSubdomain());
        }
        log.info("Tenant {} moved to {} ({})", tenant.getSubdomain(), plan.getDisplayName(), request.billingCycle());
        return subscriptionService.getSubscriptionById(sub.getId());
    }

    /**
     * A plan that cannot have extra storage drops it; moving to yearly billing starts the packs'
     * own monthly invoices from today (a monthly plan carried them on its renewal).
     */
    private void adjustAddons(Subscription sub, SubscriptionPlan plan, boolean toYearly) {
        for (SubscriptionAddon addon : addonRepository.findBySubscriptionId(sub.getId())) {
            if (FeatureCodes.STORAGE.equals(addon.getFeatureCode()) && !storageAddonOffer.availableOn(plan)) {
                addonRepository.delete(addon);
                log.info("Removed extra storage of {}: not available on {}", sub.getTenant().getSubdomain(), plan.getDisplayName());
            } else if (toYearly) {
                addon.setBilledUntil(Instant.now());
                addonRepository.save(addon);
            }
        }
    }

    /** Ends the subscription when the current period (or trial) ends; the workspace runs until then. */
    public SubscriptionResponse cancel(UUID tenantId) {
        Subscription sub = running(tenantId);
        sub.setCancelAtPeriodEnd(true);
        subscriptionRepository.save(sub);
        log.info("Tenant {} scheduled its subscription to end", sub.getTenant().getSubdomain());
        return subscriptionService.getSubscriptionById(sub.getId());
    }

    /** Takes back a scheduled cancellation. */
    public SubscriptionResponse resume(UUID tenantId) {
        Subscription sub = running(tenantId);
        sub.setCancelAtPeriodEnd(false);
        subscriptionRepository.save(sub);
        return subscriptionService.getSubscriptionById(sub.getId());
    }

    private Subscription running(UUID tenantId) {
        customer(tenantId);
        Subscription sub = subscriptionRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> EntityNotFoundException.create("Subscription for Tenant", tenantId));
        if (!RUNNING.contains(sub.getStatus())) {
            throw new BadRequestAlertException("This subscription has already ended", ENTITY_NAME, "ended");
        }
        return sub;
    }

    private Tenant customer(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> EntityNotFoundException.create("Tenant", tenantId));
        if (tenant.isDemo()) {
            throw new BadRequestAlertException("Demo workspaces have no subscription", ENTITY_NAME, "demo");
        }
        return tenant;
    }
}
