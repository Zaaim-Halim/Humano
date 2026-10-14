package com.humano.dto.billing.responses;

import com.humano.dto.billing.requests.PlanFeatureItem;
import java.util.List;
import java.util.UUID;

/**
 * The plan comparison: the feature catalog (rows) and, per plan, what it includes (cells) — what
 * explains the price differences between plans.
 */
public record PlanComparisonResponse(List<FeatureResponse> features, List<PlanItems> plans) {
    public record PlanItems(UUID planId, List<PlanFeatureItem> items) {}
}
