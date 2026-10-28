package com.humano.dev;

import com.humano.domain.billing.Coupon;
import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.InvoiceLine;
import com.humano.domain.billing.Subscription;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.billing.BillingCycle;
import com.humano.domain.enumeration.billing.DiscountType;
import com.humano.domain.enumeration.billing.InvoiceLineKind;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.domain.enumeration.tenant.ProvisioningStep;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.billing.CouponRepository;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.DefaultPlanCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dev-only data for the platform owner console: the plan catalog (Starter, Growth, Enterprise),
 * a subscription and invoice history for the demo company, a dozen <em>sample customers</em>
 * spread over the last year (with trials, a late payer, a suspended account, churn, a failed
 * provisioning), and a few coupons — so MRR, collections and the reports have something real to
 * show, and the feature catalog with what each plan includes (the comparison that explains the
 * price steps).
 * <p>
 * Sample customers are master-database rows only: they have no tenant database, so nobody can
 * sign in to them. Idempotent: skipped once the Growth plan exists.
 */
@Component
@Profile("dev")
public class PlatformDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(PlatformDemoSeeder.class);
    private static final String MARKER_PLAN = DefaultPlanCatalog.GROWTH;

    private record Sample(
        String name,
        String subdomain,
        CountryCode country,
        String timezone,
        int monthsAgo,
        String plan,
        BillingCycle cycle,
        SubscriptionStatus status,
        TenantStatus tenantStatus
    ) {}

    private static final List<Sample> SAMPLES = List.of(
        new Sample(
            "Northwind Logistics",
            "northwind",
            CountryCode.US,
            "America/Chicago",
            11,
            "Enterprise",
            BillingCycle.YEARLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Lumen Labs",
            "lumenlabs",
            CountryCode.GB,
            "Europe/London",
            10,
            "Growth",
            BillingCycle.MONTHLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Atelier Moreau",
            "ateliermoreau",
            CountryCode.FR,
            "Europe/Paris",
            9,
            "Starter",
            BillingCycle.MONTHLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Brightpath Health",
            "brightpath",
            CountryCode.CA,
            "America/Toronto",
            9,
            "Growth",
            BillingCycle.YEARLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Kestrel Engineering",
            "kestrel",
            CountryCode.DE,
            "Europe/Berlin",
            8,
            "Enterprise",
            BillingCycle.MONTHLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Harbor & Pine",
            "harborpine",
            CountryCode.NL,
            "Europe/Amsterdam",
            7,
            "Starter",
            BillingCycle.MONTHLY,
            SubscriptionStatus.CANCELLED,
            TenantStatus.DEACTIVATED
        ),
        new Sample(
            "Sable Retail Group",
            "sableretail",
            CountryCode.ES,
            "Europe/Madrid",
            6,
            "Growth",
            BillingCycle.MONTHLY,
            SubscriptionStatus.PAST_DUE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Orchid Hospitality",
            "orchid",
            CountryCode.MA,
            "Africa/Casablanca",
            5,
            "Growth",
            BillingCycle.MONTHLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Vantage Analytics",
            "vantage",
            CountryCode.IE,
            "Europe/Dublin",
            4,
            "Starter",
            BillingCycle.YEARLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Fjord Design Studio",
            "fjord",
            CountryCode.SE,
            "Europe/Stockholm",
            3,
            "Starter",
            BillingCycle.MONTHLY,
            SubscriptionStatus.SUSPENDED,
            TenantStatus.SUSPENDED
        ),
        new Sample(
            "Quill Legal",
            "quilllegal",
            CountryCode.US,
            "America/New_York",
            2,
            "Growth",
            BillingCycle.MONTHLY,
            SubscriptionStatus.CANCELLED,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Copperleaf Foods",
            "copperleaf",
            CountryCode.AU,
            "Australia/Sydney",
            1,
            "Growth",
            BillingCycle.MONTHLY,
            SubscriptionStatus.ACTIVE,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Meridian Schools",
            "meridian",
            CountryCode.PT,
            "Europe/Lisbon",
            0,
            "Enterprise",
            BillingCycle.MONTHLY,
            SubscriptionStatus.TRIAL,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Tidewater Co.",
            "tidewater",
            CountryCode.US,
            "America/Los_Angeles",
            0,
            "Starter",
            BillingCycle.MONTHLY,
            SubscriptionStatus.TRIAL,
            TenantStatus.ACTIVE
        ),
        new Sample(
            "Alpenrose Hotels",
            "alpenrose",
            CountryCode.CH,
            "Europe/Zurich",
            0,
            "Growth",
            BillingCycle.MONTHLY,
            SubscriptionStatus.PENDING_PAYMENT,
            TenantStatus.PROVISIONING_FAILED
        )
    );

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final CouponRepository couponRepository;
    private final DefaultPlanCatalog catalog;
    private final TransactionTemplate masterTx;
    private final DataSource masterDataSource;

    public PlatformDemoSeeder(
        TenantRepository tenantRepository,
        SubscriptionPlanRepository planRepository,
        SubscriptionRepository subscriptionRepository,
        InvoiceRepository invoiceRepository,
        CouponRepository couponRepository,
        DefaultPlanCatalog catalog,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Qualifier("masterDataSource") DataSource masterDataSource
    ) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.couponRepository = couponRepository;
        this.catalog = catalog;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.masterDataSource = masterDataSource;
    }

    /** Seeds the platform data once; {@code demoSubdomain} is the real demo company. */
    public void seed(String demoSubdomain) {
        boolean seeded = planRepository.findAll().stream().anyMatch(p -> MARKER_PLAN.equals(p.getDisplayName()));
        if (seeded) {
            masterTx.executeWithoutResult(status -> {
                catalog.fillMissingPricingCopy();
                catalog.addMissingFeatures();
            });
            return;
        }
        java.util.Map<UUID, Instant> joinedDates = new java.util.LinkedHashMap<>();
        masterTx.executeWithoutResult(status -> {
            SubscriptionPlan devFree = planRepository
                .findAll()
                .stream()
                .filter(p -> p.getSubscriptionType() == SubscriptionType.FREE)
                .findFirst()
                .orElse(null);
            DefaultPlanCatalog.Plans plans = catalog.create(devFree);
            SubscriptionPlan starter = plans.starter();
            SubscriptionPlan growth = plans.growth();
            SubscriptionPlan enterprise = plans.enterprise();
            Instant now = Instant.now();

            tenantRepository
                .findBySubdomain(demoSubdomain)
                .ifPresent(demo -> {
                    demo.setSubscriptionPlan(growth);
                    tenantRepository.save(demo);
                    // Yearly, paid upfront: the real demo workspace is never picked up by renewal or
                    // dunning (which would suspend it) during development.
                    subscribe(demo, growth, BillingCycle.YEARLY, SubscriptionStatus.ACTIVE, now.minus(120, ChronoUnit.DAYS), now);
                });

            for (Sample s : SAMPLES) {
                SubscriptionPlan plan =
                    switch (s.plan()) {
                        case "Starter" -> starter;
                        case "Enterprise" -> enterprise;
                        default -> growth;
                    };
                Instant joined = now.minus(s.monthsAgo() * 30L + 5, ChronoUnit.DAYS);
                Tenant tenant = new Tenant();
                tenant.setName(s.name());
                tenant.setSubdomain(s.subdomain());
                tenant.setDomain(s.subdomain() + ".com");
                tenant.setCountry(s.country());
                tenant.setTimezone(TimeZone.getTimeZone(s.timezone()));
                tenant.setStatus(s.tenantStatus());
                tenant.setProvisioningStep(
                    s.tenantStatus() == TenantStatus.PROVISIONING_FAILED ? ProvisioningStep.DATABASE_CREATED : ProvisioningStep.COMPLETED
                );
                tenant.setSubscriptionPlan(plan);
                tenant = tenantRepository.save(tenant);
                joinedDates.put(tenant.getId(), joined);
                subscribe(tenant, plan, s.cycle(), s.status(), joined, now);
            }

            coupon("WELCOME20", DiscountType.PERCENT, "20", now.plus(90, ChronoUnit.DAYS), 100, 23, true);
            coupon("ANNUAL15", DiscountType.PERCENT, "15", now.plus(365, ChronoUnit.DAYS), null, 6, true);
            coupon("PARTNER50", DiscountType.FIXED, "50", now.plus(180, ChronoUnit.DAYS), 25, 4, true);
            coupon("SPRING24", DiscountType.PERCENT, "25", now.minus(40, ChronoUnit.DAYS), 50, 50, false);
        });
        // After commit: a separate connection must see the rows.
        joinedDates.forEach((id, when) -> backdate("tenant", id, when));
        log.info("[platform-demo] seeded plans, {} sample customers, subscriptions, invoices and coupons", SAMPLES.size());
    }

    /**
     * A subscription that started on {@code start}, with one invoice per billing period up to now:
     * paid for past periods, the latest one open (overdue for a late payer), none during a trial.
     */
    private void subscribe(
        Tenant tenant,
        SubscriptionPlan plan,
        BillingCycle cycle,
        SubscriptionStatus status,
        Instant start,
        Instant now
    ) {
        Subscription sub = new Subscription();
        sub.setTenant(tenant);
        sub.setSubscriptionPlan(plan);
        sub.setBillingCycle(cycle);
        sub.setStatus(status);
        sub.setAutoRenew(status != SubscriptionStatus.CANCELLED);
        sub.setStartDate(start);
        if (status == SubscriptionStatus.TRIAL) {
            sub.setTrialStart(start);
            sub.setTrialEnd(start.plus(14, ChronoUnit.DAYS));
        }
        boolean cancelled = status == SubscriptionStatus.CANCELLED;
        Instant end = cancelled ? now.minus(12, ChronoUnit.DAYS) : null;
        sub.setEndDate(end);
        int periodDays = cycle == BillingCycle.YEARLY ? 365 : 30;
        // The current period is the one containing today (or the last one, for a cancelled
        // subscription) — the nightly renewal and dunning jobs key off it.
        Instant periodRef = end != null ? end.minus(1, ChronoUnit.DAYS) : now;
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(start, periodRef));
        Instant periodStart = start.plus((elapsed / periodDays) * periodDays, ChronoUnit.DAYS);
        sub.setCurrentPeriodStart(status == SubscriptionStatus.TRIAL ? start : periodStart);
        sub.setCurrentPeriodEnd(status == SubscriptionStatus.TRIAL ? sub.getTrialEnd() : periodStart.plus(periodDays, ChronoUnit.DAYS));
        sub = subscriptionRepository.save(sub);

        if (status == SubscriptionStatus.TRIAL || status == SubscriptionStatus.PENDING_PAYMENT) {
            return;
        }
        BigDecimal base = plan.getBasePrice();
        BigDecimal amount = cycle == BillingCycle.YEARLY
            ? base.multiply(BigDecimal.valueOf(12)).multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP)
            : base;
        Instant issue = start;
        Instant stop = end != null ? end : now;
        int n = 1;
        while (issue.isBefore(stop)) {
            Instant next = issue.plus(periodDays, ChronoUnit.DAYS);
            boolean latest = !next.isBefore(stop);
            boolean settled = issue.plus(3, ChronoUnit.DAYS).isBefore(now); // customers pay ~3 days after issue
            InvoiceStatus invoiceStatus = InvoiceStatus.PAID;
            if (latest && !cancelled) {
                if (status == SubscriptionStatus.PAST_DUE || status == SubscriptionStatus.SUSPENDED) {
                    invoiceStatus = InvoiceStatus.OVERDUE;
                } else if (!settled) {
                    invoiceStatus = InvoiceStatus.PENDING;
                }
            }
            BigDecimal tax = amount.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
            Invoice inv = new Invoice();
            inv.setTenant(tenant);
            inv.setSubscription(sub);
            inv.setInvoiceNumber("INV-" + tenant.getSubdomain().toUpperCase() + "-" + String.format("%03d", n++));
            inv.setAmount(amount);
            inv.setTaxAmount(tax);
            inv.setTaxRate(new BigDecimal("0.20"));
            inv.setTotalAmount(amount.add(tax));
            inv.setIssueDate(issue);
            inv.setDueDate(issue.plus(14, ChronoUnit.DAYS));
            inv.setStatus(invoiceStatus);
            String planName = plan.getDisplayName() != null ? plan.getDisplayName() : plan.getSubscriptionType().name();
            String billed = cycle == BillingCycle.YEARLY ? "billed yearly (15% off)" : "billed monthly";
            inv.addLine(new InvoiceLine(InvoiceLineKind.PLAN, planName + " plan, " + billed, 1, amount, issue, next));
            if (invoiceStatus == InvoiceStatus.PAID) {
                inv.setPaidDate(issue.plus(3, ChronoUnit.DAYS));
            }
            invoiceRepository.save(inv);
            issue = next;
        }
    }

    private void coupon(String code, DiscountType type, String value, Instant expiry, Integer max, int redeemed, boolean active) {
        Coupon c = new Coupon();
        c.setCode(code);
        c.setType(type);
        if (type == DiscountType.PERCENT) {
            c.setPercentage(new BigDecimal(value));
            c.setDiscount(BigDecimal.ZERO);
        } else {
            c.setDiscount(new BigDecimal(value));
        }
        c.setStartDate(Instant.now().minus(120, ChronoUnit.DAYS));
        c.setExpiryDate(expiry);
        c.setMaxRedemptions(max);
        c.setTimesRedeemed(redeemed);
        c.setActive(active);
        couponRepository.save(c);
    }

    /** {@code created_date} is set by auditing on insert; sample history needs it in the past. */
    private void backdate(String table, UUID id, Instant when) {
        // The master pool hands out connections with auto-commit off: commit explicitly.
        try (java.sql.Connection c = masterDataSource.getConnection()) {
            try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE " + table + " SET created_date = ? WHERE id = ?")) {
                ps.setTimestamp(1, java.sql.Timestamp.from(when));
                ps.setBytes(2, uuidBytes(id));
                ps.executeUpdate();
            }
            c.commit();
        } catch (java.sql.SQLException e) {
            log.warn("[platform-demo] could not backdate {} {}: {}", table, id, e.getMessage());
        }
    }

    private static byte[] uuidBytes(UUID id) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(16);
        bb.putLong(id.getMostSignificantBits());
        bb.putLong(id.getLeastSignificantBits());
        return bb.array();
    }
}
