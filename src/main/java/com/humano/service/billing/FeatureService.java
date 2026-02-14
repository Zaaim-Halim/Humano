package com.humano.service.billing;

import com.humano.domain.billing.Feature;
import com.humano.dto.billing.requests.CreateFeatureRequest;
import com.humano.dto.billing.responses.FeatureResponse;
import com.humano.repository.billing.FeatureRepository;
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
 * Service for managing features.
 * Handles CRUD operations for feature entities.
 */
@Service
public class FeatureService {

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    private final FeatureRepository featureRepository;

    public FeatureService(FeatureRepository featureRepository) {
        this.featureRepository = featureRepository;
    }

    /**
     * Create a new feature.
     *
     * @param request the feature creation request
     * @return the created feature response
     */
    @Transactional
    public FeatureResponse createFeature(CreateFeatureRequest request) {
        log.debug("Request to create Feature: {}", request);

        Feature feature = new Feature();
        feature.setName(request.name());
        feature.setDescription(request.description());
        feature.setCode(request.code());
        feature.setCategory(request.category());
        feature.setActive(true);

        Feature savedFeature = featureRepository.save(feature);
        log.info("Created feature with ID: {}", savedFeature.getId());

        return mapToResponse(savedFeature);
    }

    /**
     * Get a feature by ID.
     *
     * @param id the ID of the feature
     * @return the feature response
     */
    @Transactional(readOnly = true)
    public FeatureResponse getFeatureById(UUID id) {
        log.debug("Request to get Feature by ID: {}", id);

        return featureRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Feature", id));
    }

    /**
     * Get all features with pagination.
     *
     * @param pageable pagination information
     * @return page of feature responses
     */
    @Transactional(readOnly = true)
    public Page<FeatureResponse> getAllFeatures(Pageable pageable) {
        log.debug("Request to get all Features");

        return featureRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get features by subscription plan.
     *
     * @param subscriptionPlanId the subscription plan ID
     * @return list of feature responses
     */
    @Transactional(readOnly = true)
    public List<FeatureResponse> getFeaturesBySubscriptionPlan(UUID subscriptionPlanId) {
        log.debug("Request to get Features by SubscriptionPlan: {}", subscriptionPlanId);

        return featureRepository.findBySubscriptionPlanId(subscriptionPlanId).stream().map(this::mapToResponse).toList();
    }

    /**
     * Delete a feature by ID.
     *
     * @param id the ID of the feature to delete
     */
    @Transactional
    public void deleteFeature(UUID id) {
        log.debug("Request to delete Feature: {}", id);

        if (!featureRepository.existsById(id)) {
            throw EntityNotFoundException.create("Feature", id);
        }
        featureRepository.deleteById(id);
        log.info("Deleted feature with ID: {}", id);
    }

    private FeatureResponse mapToResponse(Feature feature) {
        return new FeatureResponse(
            feature.getId(),
            feature.getName(),
            feature.getDescription(),
            feature.getCode(),
            feature.getCategory(),
            feature.isActive(),
            feature.getCreatedBy(),
            feature.getCreatedDate(),
            feature.getLastModifiedBy(),
            feature.getLastModifiedDate()
        );
    }
}
