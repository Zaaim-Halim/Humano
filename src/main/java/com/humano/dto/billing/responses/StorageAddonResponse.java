package com.humano.dto.billing.responses;

import java.math.BigDecimal;

/**
 * {@code /api/billing/me/addons/storage}: extra storage on top of the plan. {@code available} — the
 * plan can have it; {@code packs} bought so far, each {@code packGb} GB at {@code packMonthlyPrice}
 * a month (the price they were bought at; new packs at {@code offerMonthlyPrice}).
 */
public record StorageAddonResponse(
    boolean available,
    int packGb,
    BigDecimal offerMonthlyPrice,
    String currency,
    int packs,
    BigDecimal packMonthlyPrice,
    BigDecimal monthlyTotal,
    /** Plan types that can have extra storage (a plan change to another type removes it). */
    java.util.List<String> availableOnPlanTypes
) {}
