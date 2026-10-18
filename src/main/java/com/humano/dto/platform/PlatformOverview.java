package com.humano.dto.platform;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The SaaS owner's business at a glance ({@code GET /api/platform/metrics/overview}): customers,
 * recurring revenue, collections, plan mix and the accounts that need attention. Amounts are in
 * the platform billing currency ({@code humano.billing.currency}).
 */
public record PlatformOverview(
    String currency,
    Customers customers,
    Revenue revenue,
    Subscriptions subscriptions,
    SelfServe selfServe,
    List<PlanBreakdown> plans,
    List<AttentionItem> attention
) {
    /** Customers (tenants): totals, lifecycle status and sign-ups per month (oldest first). */
    public record Customers(long total, long active, long newLast30Days, Map<String, Long> byStatus, List<MonthValue> signupsByMonth) {}

    /**
     * Recurring revenue (MRR/ARR from live subscriptions, yearly plans normalised per month),
     * average revenue per paying customer, and cash: collected per month, outstanding, overdue.
     */
    public record Revenue(
        BigDecimal mrr,
        BigDecimal arr,
        BigDecimal arpa,
        long payingCustomers,
        BigDecimal collectedLast30Days,
        BigDecimal outstanding,
        long outstandingInvoices,
        BigDecimal overdue,
        long overdueInvoices,
        List<MonthValue> collectedByMonth,
        List<MonthValue> mrrByMonth,
        /** Of the MRR, what add-ons (extra storage) bring. */
        BigDecimal addonMrr
    ) {}

    /** Subscription health: status mix, trials ending soon and churn over the last 30 days. */
    public record Subscriptions(
        Map<String, Long> byStatus,
        long trials,
        long trialsEndingIn14Days,
        long churnedLast30Days,
        double churnRate
    ) {}

    /**
     * Self-serve sign-ups confirmed in the last 30 days, and how far they got: workspace ready (trial
     * started), now on a paid plan, or setup failed (the person can retry from their link).
     */
    public record SelfServe(long confirmed, long ready, long converted, long failed) {}

    /** One plan: how many customers are on it and the MRR it brings. */
    public record PlanBreakdown(
        UUID planId,
        String name,
        String type,
        BigDecimal monthlyPrice,
        boolean active,
        long customers,
        BigDecimal mrr
    ) {}

    /** A customer needing the owner's attention (failed provisioning, payment trouble…). */
    public record AttentionItem(UUID tenantId, String tenantName, String subdomain, String kind, String detail) {}

    /** A value for a calendar month ({@code yyyy-MM}). */
    public record MonthValue(String month, BigDecimal value) {}
}
