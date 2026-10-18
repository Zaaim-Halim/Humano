package com.humano.dto.billing.responses;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/billing/me/entitlements}: what the workspace's plan allows, feature by feature, with
 * current usage for the counted ones. {@code planName} is {@code null} when no plan applies (nothing
 * is limited then). {@code limit} {@code null} = unlimited; {@code used} (in the feature's unit, e.g. GB)
 * {@code null} = not measured.
 */
public record EntitlementsResponse(String planName, List<FeatureEntitlement> features) {
    public record FeatureEntitlement(String code, UUID featureId, String name, String unit, boolean included, Integer limit, Double used) {}
}
