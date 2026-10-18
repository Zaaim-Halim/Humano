package com.humano.service.billing.entitlement;

import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.SubscriptionType;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Extra storage on sale: packs of {@code humano.billing.addons.storage.pack-gb} GB at
 * {@code monthly-price} a month each, for plans of the types in {@code plan-types} (Enterprise by
 * default — the others upgrade to get more). Configuration, not catalog data: one offer, priced once.
 */
@Component
public class StorageAddonOffer {

    private final int packGb;
    private final BigDecimal monthlyPrice;
    private final Set<SubscriptionType> planTypes;

    public StorageAddonOffer(
        @Value("${humano.billing.addons.storage.pack-gb:100}") int packGb,
        @Value("${humano.billing.addons.storage.monthly-price:25}") BigDecimal monthlyPrice,
        @Value("${humano.billing.addons.storage.plan-types:ENTERPRISE}") Set<SubscriptionType> planTypes
    ) {
        this.packGb = packGb;
        this.monthlyPrice = monthlyPrice;
        this.planTypes = planTypes;
    }

    public int packGb() {
        return packGb;
    }

    public BigDecimal monthlyPrice() {
        return monthlyPrice;
    }

    /** The plan types that can have extra storage. */
    public java.util.List<String> planTypes() {
        return planTypes.stream().map(Enum::name).sorted().toList();
    }

    /** Whether a workspace on {@code plan} can buy extra storage. */
    public boolean availableOn(SubscriptionPlan plan) {
        return plan != null && plan.getSubscriptionType() != null && planTypes.contains(plan.getSubscriptionType());
    }
}
