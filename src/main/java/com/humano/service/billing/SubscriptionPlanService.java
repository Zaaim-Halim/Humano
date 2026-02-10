package com.humano.service.billing;

import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.service.billing.dto.requests.CreateSubscriptionPlanRequest;
import com.humano.service.billing.dto.requests.UpdateSubscriptionPlanRequest;
import com.humano.service.billing.dto.responses.SubscriptionPlanResponse;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing subscription plans.
 * Handles CRUD operations for subscription plan entities.
 */
@Service
public class SubscriptionPlanService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public SubscriptionPlanService(SubscriptionPlanRepository subscriptionPlanRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    /**
     * Create a new subscription plan.
     *
     * @param request the subscription plan creation request
     * @return the created subscription plan response
     */
    @Transactional
    public SubscriptionPlanResponse createSubscriptionPlan(CreateSubscriptionPlanRequest request) {
        log.debug("Request to create SubscriptionPlan: {}", request);

        SubscriptionPlan subscriptionPlan = new SubscriptionPlan();
        subscriptionPlan.setSubscriptionType(request.subscriptionType());
        subscriptionPlan.setPrice(request.price());
        subscriptionPlan.setDisplayName(request.displayName());
        subscriptionPlan.setBasePrice(request.basePrice());
        subscriptionPlan.setActive(true);

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(subscriptionPlan);
        log.info("Created subscription plan with ID: {}", savedPlan.getId());

        return mapToResponse(savedPlan);
    }

    /**
     * Update an existing subscription plan.
     *
     * @param id the ID of the subscription plan to update
     * @param request the subscription plan update request
     * @return the updated subscription plan response
     */
    @Transactional
    public SubscriptionPlanResponse updateSubscriptionPlan(UUID id, UpdateSubscriptionPlanRequest request) {
        log.debug("Request to update SubscriptionPlan: {}", id);

        return subscriptionPlanRepository
            .findById(id)
            .map(subscriptionPlan -> {
                if (request.subscriptionType() != null) {
                    subscriptionPlan.setSubscriptionType(request.subscriptionType());
                }
                if (request.price() != null) {
                    subscriptionPlan.setPrice(request.price());
                }
                if (request.displayName() != null) {
                    subscriptionPlan.setDisplayName(request.displayName());
                }
                if (request.basePrice() != null) {
                    subscriptionPlan.setBasePrice(request.basePrice());
                }
                if (request.active() != null) {
                    subscriptionPlan.setActive(request.active());
                }
                return mapToResponse(subscriptionPlanRepository.save(subscriptionPlan));
            })
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", id));
    }

    /**
     * Get a subscription plan by ID.
     *
     * @param id the ID of the subscription plan
     * @return the subscription plan response
     */
    @Transactional(readOnly = true)
    public SubscriptionPlanResponse getSubscriptionPlanById(UUID id) {
        log.debug("Request to get SubscriptionPlan by ID: {}", id);

        return subscriptionPlanRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", id));
    }

    /**
     * Get all subscription plans with pagination.
     *
     * @param pageable pagination information
     * @return page of subscription plan responses
     */
    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponse> getAllSubscriptionPlans(Pageable pageable) {
        log.debug("Request to get all SubscriptionPlans");

        return subscriptionPlanRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get all active subscription plans.
     *
     * @return list of active subscription plan responses
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getActiveSubscriptionPlans() {
        log.debug("Request to get active SubscriptionPlans");

        return subscriptionPlanRepository.findByActiveTrue().stream().map(this::mapToResponse).toList();
    }

    /**
     * Get subscription plans by type.
     *
     * @param subscriptionType the subscription type
     * @return list of subscription plan responses
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getSubscriptionPlansByType(SubscriptionType subscriptionType) {
        log.debug("Request to get SubscriptionPlans by type: {}", subscriptionType);

        return subscriptionPlanRepository.findBySubscriptionType(subscriptionType).stream().map(this::mapToResponse).toList();
    }

    /**
     * Delete a subscription plan by ID.
     *
     * @param id the ID of the subscription plan to delete
     */
    @Transactional
    public void deleteSubscriptionPlan(UUID id) {
        log.debug("Request to delete SubscriptionPlan: {}", id);

        if (!subscriptionPlanRepository.existsById(id)) {
            throw EntityNotFoundException.create("SubscriptionPlan", id);
        }
        subscriptionPlanRepository.deleteById(id);
        log.info("Deleted subscription plan with ID: {}", id);
    }

    /**
     * Activate a subscription plan.
     *
     * @param id the ID of the subscription plan to activate
     * @return the updated subscription plan response
     */
    @Transactional
    public SubscriptionPlanResponse activateSubscriptionPlan(UUID id) {
        log.debug("Request to activate SubscriptionPlan: {}", id);

        return subscriptionPlanRepository
            .findById(id)
            .map(subscriptionPlan -> {
                subscriptionPlan.setActive(true);
                return mapToResponse(subscriptionPlanRepository.save(subscriptionPlan));
            })
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", id));
    }

    /**
     * Deactivate a subscription plan.
     *
     * @param id the ID of the subscription plan to deactivate
     * @return the updated subscription plan response
     */
    @Transactional
    public SubscriptionPlanResponse deactivateSubscriptionPlan(UUID id) {
        log.debug("Request to deactivate SubscriptionPlan: {}", id);

        return subscriptionPlanRepository
            .findById(id)
            .map(subscriptionPlan -> {
                subscriptionPlan.setActive(false);
                return mapToResponse(subscriptionPlanRepository.save(subscriptionPlan));
            })
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", id));
    }

    /**
     * Map a SubscriptionPlan entity to a SubscriptionPlanResponse DTO.
     *
     * @param subscriptionPlan the subscription plan entity
     * @return the subscription plan response DTO
     */
    private SubscriptionPlanResponse mapToResponse(SubscriptionPlan subscriptionPlan) {
        return new SubscriptionPlanResponse(
            subscriptionPlan.getId(),
            subscriptionPlan.getSubscriptionType(),
            subscriptionPlan.getPrice(),
            subscriptionPlan.getDisplayName(),
            subscriptionPlan.isActive(),
            subscriptionPlan.getBasePrice(),
            subscriptionPlan.getFeatures().size(),
            subscriptionPlan.getCreatedBy(),
            subscriptionPlan.getCreatedDate(),
            subscriptionPlan.getLastModifiedBy(),
            subscriptionPlan.getLastModifiedDate()
        );
    }
}
