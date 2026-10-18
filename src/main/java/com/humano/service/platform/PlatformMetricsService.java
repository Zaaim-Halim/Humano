package com.humano.service.platform;

import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.platform.PlatformOverview;
import com.humano.dto.platform.PlatformOverview.AttentionItem;
import com.humano.dto.platform.PlatformOverview.MonthValue;
import com.humano.dto.platform.PlatformOverview.PlanBreakdown;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the platform owner's KPIs from the master database (tenants, subscriptions,
 * invoices). Reads every row: fine for hundreds of customers; move to SQL aggregates when the
 * customer base grows.
 */
@Service
@Transactional(readOnly = true)
public class PlatformMetricsService {

    /** Subscriptions that bring recurring revenue. */
    private static final Set<SubscriptionStatus> BILLABLE = EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);
    /** Subscriptions that ended (churn). */
    private static final Set<SubscriptionStatus> ENDED = EnumSet.of(SubscriptionStatus.CANCELLED, SubscriptionStatus.EXPIRED);
    /** Yearly plans are billed 12 months at 15% off (see BillingLifecycleService). */
    private static final BigDecimal YEARLY_FACTOR = new BigDecimal("0.85");
    private static final int MONTHS = 12;

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionPlanRepository planRepository;
    private final String currency;

    public PlatformMetricsService(
        TenantRepository tenantRepository,
        SubscriptionRepository subscriptionRepository,
        InvoiceRepository invoiceRepository,
        SubscriptionPlanRepository planRepository,
        @Value("${humano.billing.currency:USD}") String currency
    ) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.planRepository = planRepository;
        this.currency = currency;
    }

    public PlatformOverview overview() {
        Instant now = Instant.now();
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        // Demo workspaces are not customers: no signups, no status mix.
        List<Tenant> tenants = tenantRepository.findAll().stream().filter(t -> !t.isDemo()).toList();
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        List<Invoice> invoices = invoiceRepository.findAll();
        List<YearMonth> months = lastMonths(now);

        // ---- Customers
        Map<String, Long> tenantsByStatus = new LinkedHashMap<>();
        for (Tenant t : tenants) {
            tenantsByStatus.merge(String.valueOf(t.getStatus()), 1L, Long::sum);
        }
        long activeTenants = tenantsByStatus.getOrDefault("ACTIVE", 0L);
        long newTenants = tenants.stream().filter(t -> t.getCreatedDate() != null && t.getCreatedDate().isAfter(monthAgo)).count();
        Map<YearMonth, BigDecimal> signups = zeroed(months);
        for (Tenant t : tenants) {
            if (t.getCreatedDate() != null) signups.computeIfPresent(month(t.getCreatedDate()), (k, v) -> v.add(BigDecimal.ONE));
        }

        // ---- Recurring revenue
        List<Subscription> billable = subscriptions.stream().filter(s -> BILLABLE.contains(s.getStatus())).toList();
        BigDecimal mrr = billable.stream().map(PlatformMetricsService::monthlyValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        long paying = billable.stream().filter(s -> monthlyValue(s).signum() > 0).map(s -> s.getTenant().getId()).distinct().count();
        BigDecimal arpa = paying == 0 ? BigDecimal.ZERO : mrr.divide(BigDecimal.valueOf(paying), 2, RoundingMode.HALF_UP);

        // MRR at the end of each month: subscriptions that had started and not yet ended.
        Map<YearMonth, BigDecimal> mrrByMonth = zeroed(months);
        for (YearMonth m : months) {
            Instant end = m.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            BigDecimal total = subscriptions
                .stream()
                .filter(s -> s.getStatus() != SubscriptionStatus.TRIAL && s.getStatus() != SubscriptionStatus.PENDING_PAYMENT)
                .filter(s -> s.getStartDate() != null && s.getStartDate().isBefore(end))
                .filter(s -> !ENDED.contains(s.getStatus()) || (s.getEndDate() != null && s.getEndDate().isAfter(end)))
                .map(PlatformMetricsService::monthlyValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            mrrByMonth.put(m, total);
        }

        // ---- Collections
        Map<YearMonth, BigDecimal> collected = zeroed(months);
        BigDecimal collected30 = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        long outstandingCount = 0;
        long overdueCount = 0;
        for (Invoice inv : invoices) {
            BigDecimal amount = total(inv);
            if (inv.getStatus() == InvoiceStatus.PAID && inv.getPaidDate() != null) {
                collected.computeIfPresent(month(inv.getPaidDate()), (k, v) -> v.add(amount));
                if (inv.getPaidDate().isAfter(monthAgo)) collected30 = collected30.add(amount);
            } else if (inv.getStatus() == InvoiceStatus.PENDING || inv.getStatus() == InvoiceStatus.OVERDUE) {
                outstanding = outstanding.add(amount);
                outstandingCount++;
                boolean late = inv.getStatus() == InvoiceStatus.OVERDUE || (inv.getDueDate() != null && inv.getDueDate().isBefore(now));
                if (late) {
                    overdue = overdue.add(amount);
                    overdueCount++;
                }
            }
        }

        // ---- Subscription health
        Map<String, Long> subsByStatus = new LinkedHashMap<>();
        for (Subscription s : subscriptions) {
            subsByStatus.merge(String.valueOf(s.getStatus()), 1L, Long::sum);
        }
        long trials = subsByStatus.getOrDefault("TRIAL", 0L);
        Instant inTwoWeeks = now.plus(14, ChronoUnit.DAYS);
        long trialsEnding = subscriptions
            .stream()
            .filter(s -> s.getStatus() == SubscriptionStatus.TRIAL && s.getTrialEnd() != null)
            .filter(s -> s.getTrialEnd().isAfter(now) && s.getTrialEnd().isBefore(inTwoWeeks))
            .count();
        long churned = subscriptions
            .stream()
            .filter(s -> ENDED.contains(s.getStatus()))
            .filter(s -> endedAt(s) != null && endedAt(s).isAfter(monthAgo))
            .count();
        long base = billable.size() + churned;
        double churnRate = base == 0 ? 0 : Math.round((churned * 1000.0) / base) / 10.0;

        // ---- Self-serve sign-ups (the demo contact fields record who signed up)
        Map<UUID, Subscription> subByTenant = new HashMap<>();
        for (Subscription s : subscriptions) {
            subByTenant.put(s.getTenant().getId(), s);
        }
        List<Tenant> selfServe = tenants
            .stream()
            .filter(t -> t.getDemoContactEmail() != null && t.getCreatedDate() != null && t.getCreatedDate().isAfter(monthAgo))
            .toList();
        long ready = selfServe.stream().filter(t -> subByTenant.containsKey(t.getId())).count();
        long converted = selfServe
            .stream()
            .map(t -> subByTenant.get(t.getId()))
            .filter(s -> s != null && BILLABLE.contains(s.getStatus()))
            .count();
        long failed = selfServe.stream().filter(t -> t.getStatus() == TenantStatus.PROVISIONING_FAILED).count();

        // ---- Plans
        List<PlanBreakdown> plans = new ArrayList<>();
        for (SubscriptionPlan plan : planRepository.findAll()) {
            List<Subscription> onPlan = billable
                .stream()
                .filter(s -> Objects.equals(s.getSubscriptionPlan().getId(), plan.getId()))
                .toList();
            plans.add(
                new PlanBreakdown(
                    plan.getId(),
                    plan.getDisplayName() != null ? plan.getDisplayName() : String.valueOf(plan.getSubscriptionType()),
                    String.valueOf(plan.getSubscriptionType()),
                    planPrice(plan),
                    plan.isActive(),
                    onPlan.size(),
                    onPlan.stream().map(PlatformMetricsService::monthlyValue).reduce(BigDecimal.ZERO, BigDecimal::add)
                )
            );
        }
        plans.sort(Comparator.comparing(PlanBreakdown::monthlyPrice));

        return new PlatformOverview(
            currency,
            new PlatformOverview.Customers(tenants.size(), activeTenants, newTenants, tenantsByStatus, series(signups)),
            new PlatformOverview.Revenue(
                scale(mrr),
                scale(mrr.multiply(BigDecimal.valueOf(12))),
                arpa,
                paying,
                scale(collected30),
                scale(outstanding),
                outstandingCount,
                scale(overdue),
                overdueCount,
                series(collected),
                series(mrrByMonth)
            ),
            new PlatformOverview.Subscriptions(subsByStatus, trials, trialsEnding, churned, churnRate),
            new PlatformOverview.SelfServe(selfServe.size(), ready, converted, failed),
            plans,
            attention(tenants, subscriptions, invoices, now)
        );
    }

    /** Customers needing action: provisioning/migration failures, payment trouble, overdue invoices. */
    private List<AttentionItem> attention(List<Tenant> tenants, List<Subscription> subscriptions, List<Invoice> invoices, Instant now) {
        List<AttentionItem> items = new ArrayList<>();
        for (Tenant t : tenants) {
            String status = String.valueOf(t.getStatus());
            if (status.equals("PROVISIONING_FAILED") || status.equals("MIGRATION_FAILED")) {
                items.add(item(t, status, null));
            }
        }
        for (Subscription s : subscriptions) {
            if (s.getStatus() == SubscriptionStatus.PAST_DUE || s.getStatus() == SubscriptionStatus.SUSPENDED) {
                items.add(item(s.getTenant(), "SUBSCRIPTION_" + s.getStatus(), null));
            }
        }
        Map<UUID, BigDecimal> overdueByTenant = new LinkedHashMap<>();
        for (Invoice inv : invoices) {
            boolean open = inv.getStatus() == InvoiceStatus.PENDING || inv.getStatus() == InvoiceStatus.OVERDUE;
            boolean late = inv.getStatus() == InvoiceStatus.OVERDUE || (inv.getDueDate() != null && inv.getDueDate().isBefore(now));
            if (open && late && inv.getTenant() != null) overdueByTenant.merge(inv.getTenant().getId(), total(inv), BigDecimal::add);
        }
        for (Tenant t : tenants) {
            BigDecimal amount = overdueByTenant.get(t.getId());
            if (amount != null) items.add(item(t, "INVOICE_OVERDUE", scale(amount).toPlainString()));
        }
        return items;
    }

    private static AttentionItem item(Tenant t, String kind, String detail) {
        return new AttentionItem(t.getId(), t.getName(), t.getSubdomain(), kind, detail);
    }

    /** What a subscription is worth per month (yearly plans billed at 12 × 85%). */
    static BigDecimal monthlyValue(Subscription s) {
        BigDecimal price = planPrice(s.getSubscriptionPlan());
        return s.getBillingCycle() == BillingCycle.YEARLY ? price.multiply(YEARLY_FACTOR) : price;
    }

    private static BigDecimal planPrice(SubscriptionPlan plan) {
        if (plan == null) return BigDecimal.ZERO;
        BigDecimal p = plan.getBasePrice() != null ? plan.getBasePrice() : plan.getPrice();
        return p != null ? p : BigDecimal.ZERO;
    }

    private static Instant endedAt(Subscription s) {
        return s.getEndDate() != null ? s.getEndDate() : s.getLastModifiedDate();
    }

    private static BigDecimal total(Invoice inv) {
        if (inv.getTotalAmount() != null) return inv.getTotalAmount();
        return inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO;
    }

    private static List<YearMonth> lastMonths(Instant now) {
        YearMonth current = month(now);
        List<YearMonth> months = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) months.add(current.minusMonths(i));
        return months;
    }

    private static Map<YearMonth, BigDecimal> zeroed(List<YearMonth> months) {
        Map<YearMonth, BigDecimal> map = new LinkedHashMap<>();
        months.forEach(m -> map.put(m, BigDecimal.ZERO));
        return map;
    }

    private static List<MonthValue> series(Map<YearMonth, BigDecimal> map) {
        return map.entrySet().stream().map(e -> new MonthValue(e.getKey().toString(), scale(e.getValue()))).toList();
    }

    private static YearMonth month(Instant instant) {
        return YearMonth.from(instant.atZone(ZoneOffset.UTC));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
