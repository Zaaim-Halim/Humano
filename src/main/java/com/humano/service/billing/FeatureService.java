package com.humano.service.billing;

import com.humano.domain.billing.Feature;
import com.humano.domain.billing.PlanFeature;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.FeatureLimitType;
import com.humano.dto.billing.requests.CreateFeatureRequest;
import com.humano.dto.billing.requests.PlanFeatureItem;
import com.humano.dto.billing.responses.FeatureResponse;
import com.humano.dto.billing.responses.PlanComparisonResponse;
import com.humano.repository.billing.FeatureRepository;
import com.humano.repository.billing.PlanFeatureRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The plan feature catalog and what each plan includes of it (the plan comparison). The catalog is
 * shared by every plan; each plan sets, per feature, whether it is included and at which limit.
 */
@Service
@Transactional
public class FeatureService {

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionPlanRepository planRepository;

    public FeatureService(
        FeatureRepository featureRepository,
        PlanFeatureRepository planFeatureRepository,
        SubscriptionPlanRepository planRepository
    ) {
        this.featureRepository = featureRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.planRepository = planRepository;
    }

    /** The catalog (display order) and every plan's cells. */
    @Transactional(readOnly = true)
    public PlanComparisonResponse comparison() {
        List<FeatureResponse> features = featureRepository
            .findAllByOrderBySortOrderAscNameAsc()
            .stream()
            .filter(Feature::isActive)
            .map(this::mapToResponse)
            .toList();
        Map<UUID, List<PlanFeatureItem>> byPlan = new LinkedHashMap<>();
        planRepository.findAll().forEach(p -> byPlan.put(p.getId(), new ArrayList<>()));
        for (PlanFeature pf : planFeatureRepository.findAllWithPlanAndFeature()) {
            byPlan
                .computeIfAbsent(pf.getPlan().getId(), k -> new ArrayList<>())
                .add(new PlanFeatureItem(pf.getFeature().getId(), pf.isIncluded(), pf.getLimitValue(), pf.getNote()));
        }
        return new PlanComparisonResponse(
            features,
            byPlan.entrySet().stream().map(e -> new PlanComparisonResponse.PlanItems(e.getKey(), e.getValue())).toList()
        );
    }

    public FeatureResponse createFeature(CreateFeatureRequest request) {
        String code = codeOf(request.code() != null && !request.code().isBlank() ? request.code() : request.name());
        if (featureRepository.findOneByCode(code).isPresent()) {
            throw new BadRequestAlertException("A feature with this code already exists", "feature", "codeexists");
        }
        Feature feature = new Feature();
        feature.setCode(code);
        apply(feature, request);
        feature.setActive(true);
        Feature saved = featureRepository.save(feature);
        log.info("Created feature {}", saved.getCode());
        return mapToResponse(saved);
    }

    public FeatureResponse updateFeature(UUID id, CreateFeatureRequest request) {
        Feature feature = featureRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("Feature", id));
        apply(feature, request);
        return mapToResponse(featureRepository.save(feature));
    }

    /** Removes a feature from the catalog and from every plan. */
    public void deleteFeature(UUID id) {
        Feature feature = featureRepository.findById(id).orElseThrow(() -> EntityNotFoundException.create("Feature", id));
        planRepository.findAll().forEach(p -> p.getFeatures().removeIf(pf -> pf.getFeature().getId().equals(id)));
        featureRepository.delete(feature);
    }

    /** Replaces what a plan includes (features not listed are left out of the plan). */
    public void setPlanFeatures(UUID planId, List<PlanFeatureItem> items) {
        SubscriptionPlan plan = planRepository
            .findById(planId)
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", planId));
        Map<UUID, Feature> catalog = featureRepository.findAll().stream().collect(Collectors.toMap(Feature::getId, Function.identity()));
        Map<UUID, PlanFeature> existing = plan
            .getFeatures()
            .stream()
            .collect(Collectors.toMap(pf -> pf.getFeature().getId(), Function.identity()));
        plan.getFeatures().removeIf(pf -> items.stream().noneMatch(i -> i.featureId().equals(pf.getFeature().getId())));
        for (PlanFeatureItem item : items) {
            Feature feature = catalog.get(item.featureId());
            if (feature == null) {
                throw EntityNotFoundException.create("Feature", item.featureId());
            }
            PlanFeature pf = existing.get(item.featureId());
            if (pf == null) {
                pf = new PlanFeature();
                pf.setPlan(plan);
                pf.setFeature(feature);
                plan.getFeatures().add(pf);
            }
            pf.setIncluded(item.included());
            pf.setLimitValue(item.included() && feature.getLimitType() == FeatureLimitType.QUANTITY ? item.limitValue() : null);
            pf.setNote(item.note() == null || item.note().isBlank() ? null : item.note().trim());
        }
        planRepository.save(plan);
    }

    private static void apply(Feature feature, CreateFeatureRequest request) {
        feature.setName(request.name().trim());
        feature.setDescription(request.description());
        feature.setCategory(request.category());
        feature.setLimitType(request.limitType() != null ? request.limitType() : FeatureLimitType.BOOLEAN);
        feature.setUnit(request.unit());
        if (request.sortOrder() != null) {
            feature.setSortOrder(request.sortOrder());
        }
    }

    /** "Admin seats" → "ADMIN_SEATS". */
    private static String codeOf(String text) {
        String code = text.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return code.length() > 50 ? code.substring(0, 50) : code;
    }

    private FeatureResponse mapToResponse(Feature feature) {
        return new FeatureResponse(
            feature.getId(),
            feature.getName(),
            feature.getDescription(),
            feature.getCode(),
            feature.getCategory(),
            feature.isActive(),
            feature.getLimitType(),
            feature.getUnit(),
            feature.getSortOrder(),
            feature.getCreatedBy(),
            feature.getCreatedDate(),
            feature.getLastModifiedBy(),
            feature.getLastModifiedDate()
        );
    }

    /** Catalog in display order (used by the seeder). */
    @Transactional(readOnly = true)
    public List<Feature> catalog() {
        return featureRepository.findAll().stream().sorted(Comparator.comparing(Feature::getSortOrder)).toList();
    }
}
