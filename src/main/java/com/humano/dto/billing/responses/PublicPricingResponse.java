package com.humano.dto.billing.responses;

import com.humano.domain.enumeration.billing.FeatureLimitType;
import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.dto.billing.requests.PlanFeatureItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the public pricing page shows (anonymous): the listed plans, cheapest first, and the active
 * feature catalog they are compared on. No audit fields — this leaves the platform.
 */
public record PublicPricingResponse(String currency, List<Plan> plans, List<Feature> features) {
    /** {@code monthlyPrice} is the plan's base price, else its price. */
    public record Plan(
        UUID id,
        SubscriptionType type,
        String name,
        String description,
        BigDecimal monthlyPrice,
        boolean featured,
        List<PlanFeatureItem> items
    ) {}

    public record Feature(UUID id, String name, String description, String category, FeatureLimitType limitType, String unit) {}
}
