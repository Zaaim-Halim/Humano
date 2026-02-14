package com.humano.service.billing;

import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.CreateSubscriptionRequest;
import com.humano.dto.billing.requests.UpdateSubscriptionRequest;
import com.humano.dto.billing.responses.SubscriptionResponse;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing subscriptions.
 * Handles CRUD operations and subscription lifecycle management.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String ENTITY_NAME = "subscription";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantRepository tenantRepository;

    public SubscriptionService(
        SubscriptionRepository subscriptionRepository,
        SubscriptionPlanRepository subscriptionPlanRepository,
        TenantRepository tenantRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Create a new subscription.
     *
     * @param request the subscription creation request
     * @return the created subscription response
     */
    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        log.debug("Request to create Subscription: {}", request);

        // Check if tenant already has an active subscription
        subscriptionRepository
            .findByTenantId(request.tenantId())
            .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE || s.getStatus() == SubscriptionStatus.TRIAL)
            .ifPresent(s -> {
                throw new BadRequestAlertException("Tenant already has an active subscription", ENTITY_NAME, "subscriptionexists");
            });

        Tenant tenant = tenantRepository
            .findById(request.tenantId())
            .orElseThrow(() -> EntityNotFoundException.create("Tenant", request.tenantId()));

        SubscriptionPlan plan = subscriptionPlanRepository
            .findById(request.subscriptionPlanId())
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", request.subscriptionPlanId()));

        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setSubscriptionPlan(plan);
        subscription.setBillingCycle(request.billingCycle());
        subscription.setAutoRenew(request.autoRenew() != null ? request.autoRenew() : true);
        subscription.setStartDate(now);
        subscription.setCurrentPeriodStart(now);

        // Set current period end based on billing cycle
        Instant periodEnd =
            switch (request.billingCycle()) {
                case MONTHLY -> now.plus(30, ChronoUnit.DAYS);
                case YEARLY -> now.plus(365, ChronoUnit.DAYS);
            };
        subscription.setCurrentPeriodEnd(periodEnd);

        // Handle trial period
        if (request.trialEnd() != null && request.trialEnd().isAfter(now)) {
            subscription.setTrialStart(now);
            subscription.setTrialEnd(request.trialEnd());
            subscription.setStatus(SubscriptionStatus.TRIAL);
        } else {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }

        Subscription savedSubscription = subscriptionRepository.save(subscription);
        log.info("Created subscription with ID: {}", savedSubscription.getId());

        return mapToResponse(savedSubscription);
    }

    /**
     * Update an existing subscription.
     *
     * @param id the ID of the subscription to update
     * @param request the subscription update request
     * @return the updated subscription response
     */
    @Transactional
    public SubscriptionResponse updateSubscription(UUID id, UpdateSubscriptionRequest request) {
        log.debug("Request to update Subscription: {}", id);

        return subscriptionRepository
            .findById(id)
            .map(subscription -> {
                if (request.subscriptionPlanId() != null) {
                    SubscriptionPlan plan = subscriptionPlanRepository
                        .findById(request.subscriptionPlanId())
                        .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", request.subscriptionPlanId()));
                    subscription.setSubscriptionPlan(plan);
                }
                if (request.billingCycle() != null) {
                    subscription.setBillingCycle(request.billingCycle());
                }
                if (request.autoRenew() != null) {
                    subscription.setAutoRenew(request.autoRenew());
                }
                if (request.status() != null) {
                    subscription.setStatus(request.status());
                }
                if (request.cancelAtPeriodEnd() != null) {
                    subscription.setCancelAtPeriodEnd(request.cancelAtPeriodEnd());
                }
                return mapToResponse(subscriptionRepository.save(subscription));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Subscription", id));
    }

    /**
     * Get a subscription by ID.
     *
     * @param id the ID of the subscription
     * @return the subscription response
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscriptionById(UUID id) {
        log.debug("Request to get Subscription by ID: {}", id);

        return subscriptionRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Subscription", id));
    }

    /**
     * Get subscription by tenant.
     *
     * @param tenantId the tenant ID
     * @return the subscription response
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscriptionByTenant(UUID tenantId) {
        log.debug("Request to get Subscription by Tenant: {}", tenantId);

        return subscriptionRepository
            .findByTenantId(tenantId)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Subscription for Tenant", tenantId));
    }

    /**
     * Get all subscriptions with pagination.
     *
     * @param pageable pagination information
     * @return page of subscription responses
     */
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getAllSubscriptions(Pageable pageable) {
        log.debug("Request to get all Subscriptions");

        return subscriptionRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Cancel a subscription.
     *
     * @param id the ID of the subscription to cancel
     * @param immediate whether to cancel immediately or at period end
     * @return the updated subscription response
     */
    @Transactional
    public SubscriptionResponse cancelSubscription(UUID id, boolean immediate) {
        log.debug("Request to cancel Subscription: {} (immediate: {})", id, immediate);

        return subscriptionRepository
            .findById(id)
            .map(subscription -> {
                if (immediate) {
                    subscription.setStatus(SubscriptionStatus.CANCELLED);
                    subscription.setEndDate(Instant.now());
                } else {
                    subscription.setCancelAtPeriodEnd(true);
                }
                return mapToResponse(subscriptionRepository.save(subscription));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Subscription", id));
    }

    /**
     * Delete a subscription by ID.
     *
     * @param id the ID of the subscription to delete
     */
    @Transactional
    public void deleteSubscription(UUID id) {
        log.debug("Request to delete Subscription: {}", id);

        if (!subscriptionRepository.existsById(id)) {
            throw EntityNotFoundException.create("Subscription", id);
        }
        subscriptionRepository.deleteById(id);
        log.info("Deleted subscription with ID: {}", id);
    }

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return new SubscriptionResponse(
            subscription.getId(),
            subscription.getTenant().getId(),
            subscription.getTenant().getName(),
            subscription.getSubscriptionPlan().getId(),
            subscription.getSubscriptionPlan().getDisplayName(),
            subscription.getStartDate(),
            subscription.getEndDate(),
            subscription.getStatus(),
            subscription.getAutoRenew(),
            subscription.getBillingCycle(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            subscription.getCancelAtPeriodEnd(),
            subscription.getTrialStart(),
            subscription.getTrialEnd(),
            subscription.getCreatedBy(),
            subscription.getCreatedDate(),
            subscription.getLastModifiedBy(),
            subscription.getLastModifiedDate()
        );
    }
}
