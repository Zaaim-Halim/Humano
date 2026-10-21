package com.humano.service.billing;

import com.humano.domain.billing.Feature;
import com.humano.domain.billing.PlanFeature;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.dto.billing.requests.PlanFeatureItem;
import com.humano.dto.billing.responses.PublicPricingResponse;
import com.humano.repository.billing.FeatureRepository;
import com.humano.repository.billing.PlanFeatureRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public pricing page's data: active plans the platform lists publicly, compared on the active
 * feature catalog. Inactive features are left out of each plan's cells too.
 */
@Service
@Transactional(readOnly = true)
public class PublicPricingService {

    private final SubscriptionPlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final String currency;

    public PublicPricingService(
        SubscriptionPlanRepository planRepository,
        FeatureRepository featureRepository,
        PlanFeatureRepository planFeatureRepository,
        @Value("${humano.billing.currency:USD}") String currency
    ) {
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.currency = currency;
    }

    public PublicPricingResponse pricing() {
        List<Feature> features = featureRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(Feature::isActive).toList();
        Set<UUID> featureIds = features.stream().map(Feature::getId).collect(Collectors.toSet());

        Map<UUID, List<PlanFeatureItem>> itemsByPlan = new HashMap<>();
        for (PlanFeature pf : planFeatureRepository.findAllWithPlanAndFeature()) {
            if (featureIds.contains(pf.getFeature().getId())) {
                itemsByPlan
                    .computeIfAbsent(pf.getPlan().getId(), k -> new ArrayList<>())
                    .add(new PlanFeatureItem(pf.getFeature().getId(), pf.isIncluded(), pf.getLimitValue(), pf.getNote()));
            }
        }

        List<PublicPricingResponse.Plan> plans = planRepository
            .findByActiveTrue()
            .stream()
            .filter(SubscriptionPlan::isPublicListed)
            .sorted(Comparator.comparing(PublicPricingService::monthlyPrice))
            .map(p ->
                new PublicPricingResponse.Plan(
                    p.getId(),
                    p.getSubscriptionType(),
                    p.getDisplayName() != null ? p.getDisplayName() : p.getSubscriptionType().name(),
                    p.getDescription(),
                    monthlyPrice(p),
                    p.isFeatured(),
                    itemsByPlan.getOrDefault(p.getId(), List.of())
                )
            )
            .toList();

        return new PublicPricingResponse(
            currency,
            plans,
            features
                .stream()
                .map(f ->
                    new PublicPricingResponse.Feature(
                        f.getId(),
                        f.getCode(),
                        f.getName(),
                        f.getDescription(),
                        f.getCategory(),
                        f.getLimitType(),
                        f.getUnit()
                    )
                )
                .toList()
        );
    }

    private static BigDecimal monthlyPrice(SubscriptionPlan plan) {
        return plan.getBasePrice() != null ? plan.getBasePrice() : plan.getPrice();
    }
}
