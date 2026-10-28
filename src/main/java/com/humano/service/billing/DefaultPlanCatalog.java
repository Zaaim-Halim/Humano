package com.humano.service.billing;

import com.humano.domain.billing.Feature;
import com.humano.domain.billing.PlanFeature;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.billing.FeatureLimitType;
import com.humano.domain.enumeration.billing.SubscriptionType;
import com.humano.repository.billing.FeatureRepository;
import com.humano.repository.billing.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The plans and feature catalog a new installation starts with: Free (not on the pricing page), Starter,
 * Growth and Enterprise, compared on the features in {@link #CATALOG}. On the first start of an empty
 * database — no plans and no features at all — they are created ({@link #seedIfEmpty}), so production
 * has a pricing page and plans to sign up for from day one. From then on the platform console (Plans)
 * owns them: nothing here changes a plan or feature that exists. Development builds the same catalog
 * through {@code PlatformDemoSeeder}, so the two never drift apart. The feature codes the app checks to
 * switch modules on and off are in {@link com.humano.service.billing.entitlement.FeatureCodes}.
 */
@Component
public class DefaultPlanCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPlanCatalog.class);

    public static final String FREE = "Free";
    public static final String STARTER = "Starter";
    public static final String GROWTH = "Growth";
    public static final String ENTERPRISE = "Enterprise";

    /** One row of the comparison: the catalog feature and its value per plan (free, starter, growth, enterprise). */
    public record Row(String category, String code, String name, String description, FeatureLimitType type, String unit, Object[] values) {}

    /** The four plans, in catalog column order; {@code free} may be null when an installation has none. */
    public record Plans(SubscriptionPlan free, SubscriptionPlan starter, SubscriptionPlan growth, SubscriptionPlan enterprise) {
        SubscriptionPlan[] columns() {
            return new SubscriptionPlan[] { free, starter, growth, enterprise };
        }
    }

    /** {@code true}/{@code false} = included or not; a number = limit; {@code null} = unlimited; a string = included, with that note. */
    public static final List<Row> CATALOG = List.of(
        new Row(
            "Core HR",
            "EMPLOYEES",
            "Employees",
            "People you can manage in Humano.",
            FeatureLimitType.QUANTITY,
            "employees",
            new Object[] { 5, 25, 150, null }
        ),
        new Row(
            "Core HR",
            "ADMIN_SEATS",
            "HR admin seats",
            "People with HR or payroll admin access.",
            FeatureLimitType.QUANTITY,
            "admins",
            new Object[] { 1, 3, 10, null }
        ),
        new Row(
            "Core HR",
            "DIRECTORY",
            "Directory, org chart & people tree",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { true, true, true, true }
        ),
        new Row(
            "Core HR",
            "DOCUMENTS",
            "Document storage",
            "Contracts, IDs and payslips.",
            FeatureLimitType.QUANTITY,
            "GB",
            // Enterprise: 1 TB included; more can be added to a workspace.
            new Object[] { 1, 10, 100, 1024 }
        ),
        new Row(
            "Core HR",
            "DEDICATED_DB",
            "Dedicated, isolated database",
            "Your data never shares a database with another company.",
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { true, true, true, true }
        ),
        new Row(
            "Time & leave",
            "LEAVE",
            "Leave requests & calendar",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { true, true, true, true }
        ),
        new Row(
            "Time & leave",
            "TIMESHEETS",
            "Timesheets & time clock",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, true, true, true }
        ),
        new Row(
            "Time & leave",
            "APPROVAL_CHAINS",
            "Multi-step approval chains",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, true, true }
        ),
        new Row(
            "Payroll",
            "PAYROLL",
            "Payroll runs & payslips",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, true, true, true }
        ),
        new Row(
            "Payroll",
            "PAYROLL_TEMPLATES",
            "Statutory payroll templates",
            "Ready-made income tax and social contribution rules for 13 countries, updated for each new tax year.",
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, true, true, true }
        ),
        new Row(
            "Payroll",
            "PAYROLL_COUNTRIES",
            "Payroll countries",
            "Countries you can run payroll in.",
            FeatureLimitType.QUANTITY,
            "countries",
            new Object[] { false, 1, 3, null }
        ),
        new Row(
            "Payroll",
            "COMPENSATION",
            "Salary bands, bonuses & benefits",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, true, true }
        ),
        new Row(
            "Talent",
            "PERFORMANCE",
            "Performance reviews",
            "Review cycles with self-assessment, manager review, 360° feedback, calibration (9-box), goals and improvement plans.",
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, true, true }
        ),
        new Row(
            "Talent",
            "TRAINING",
            "Training & certifications",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, true, true }
        ),
        new Row("Insights", "DASHBOARDS", "Dashboards", null, FeatureLimitType.BOOLEAN, null, new Object[] { true, true, true, true }),
        new Row(
            "Insights",
            "ANALYTICS",
            "Advanced analytics & exports",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, true, true }
        ),
        new Row("Insights", "API", "API access", null, FeatureLimitType.BOOLEAN, null, new Object[] { false, false, false, true }),
        new Row(
            "Security & support",
            "AUDIT_LOG",
            "Audit log",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, true, true }
        ),
        new Row(
            "Security & support",
            "SSO",
            "Single sign-on (SAML)",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, false, true }
        ),
        new Row(
            "Security & support",
            "SUPPORT",
            "Support",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { "Community", "Email", "Email & chat", "Dedicated manager" }
        ),
        new Row(
            "Security & support",
            "SLA",
            "Uptime SLA",
            null,
            FeatureLimitType.BOOLEAN,
            null,
            new Object[] { false, false, false, "99.9%" }
        )
    );

    private final SubscriptionPlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final TransactionTemplate masterTx;
    private final boolean enabled;

    public DefaultPlanCatalog(
        SubscriptionPlanRepository planRepository,
        FeatureRepository featureRepository,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Value("${humano.billing.default-catalog.enabled:true}") boolean enabled
    ) {
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.enabled = enabled;
    }

    /**
     * Creates the default plans and catalog on an empty database; does nothing once any plan or feature
     * exists, so a catalog edited (or emptied on purpose) in the console is never touched.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (!enabled) {
            return;
        }
        try {
            Boolean created = masterTx.execute(status -> {
                if (planRepository.count() > 0 || featureRepository.count() > 0) {
                    return false;
                }
                create(null);
                return true;
            });
            if (Boolean.TRUE.equals(created)) {
                LOG.info(
                    "Created the default plans ({}, {}, {}, {}) and {} catalog features. Review the prices in the platform console under Plans.",
                    FREE,
                    STARTER,
                    GROWTH,
                    ENTERPRISE,
                    CATALOG.size()
                );
            }
        } catch (Exception e) {
            LOG.error("Could not create the default plans and catalog", e);
        }
    }

    /**
     * Creates Starter, Growth and Enterprise, every catalog feature, and each plan's value for it. The Free
     * column goes to {@code free} when given (development already has one), else to a new Free plan. Runs in
     * the caller's master transaction.
     */
    public Plans create(SubscriptionPlan free) {
        Plans plans = new Plans(
            free != null ? free : plan(SubscriptionType.FREE, FREE, "0"),
            plan(SubscriptionType.BASIC, STARTER, "49"),
            plan(SubscriptionType.PRO, GROWTH, "149"),
            plan(SubscriptionType.ENTERPRISE, ENTERPRISE, "499")
        );
        SubscriptionPlan[] columns = plans.columns();
        int order = 0;
        for (Row row : CATALOG) {
            Feature feature = featureRepository.save(feature(row, order++));
            for (int i = 0; i < columns.length; i++) {
                columns[i].getFeatures().add(planFeature(columns[i], feature, row.values()[i]));
            }
        }
        for (SubscriptionPlan plan : columns) {
            planRepository.save(plan);
        }
        return plans;
    }

    /**
     * Databases created before a feature joined {@link #CATALOG}: add it once, in its catalog place and with
     * its value on each default plan still there. Features already present are left as they are, edits
     * included. Runs in the caller's master transaction; used by the development seeder.
     */
    public void addMissingFeatures() {
        List<SubscriptionPlan> all = planRepository.findAll();
        SubscriptionPlan[] columns = {
            all.stream().filter(p -> p.getSubscriptionType() == SubscriptionType.FREE).findFirst().orElse(null),
            all.stream().filter(p -> STARTER.equals(p.getDisplayName())).findFirst().orElse(null),
            all.stream().filter(p -> GROWTH.equals(p.getDisplayName())).findFirst().orElse(null),
            all.stream().filter(p -> ENTERPRISE.equals(p.getDisplayName())).findFirst().orElse(null),
        };
        Feature previous = null;
        for (Row row : CATALOG) {
            Feature existing = featureRepository.findOneByCode(row.code()).orElse(null);
            if (existing != null) {
                previous = existing;
                continue;
            }
            // Right after the catalog row before it; later features move down one.
            int order = previous != null ? previous.getSortOrder() + 1 : 0;
            for (Feature later : featureRepository.findAll()) {
                if (later.getSortOrder() >= order) {
                    later.setSortOrder(later.getSortOrder() + 1);
                    featureRepository.save(later);
                }
            }
            Feature feature = featureRepository.save(feature(row, order));
            for (int i = 0; i < columns.length; i++) {
                if (columns[i] != null) {
                    columns[i].getFeatures().add(planFeature(columns[i], feature, row.values()[i]));
                    planRepository.save(columns[i]);
                }
            }
            LOG.info("Added plan feature '{}' to the catalog", row.code());
            previous = feature;
        }
    }

    /** Plans created before they had pricing copy: fill it in once, never over an edit. */
    public void fillMissingPricingCopy() {
        for (SubscriptionPlan plan : planRepository.findAll()) {
            boolean untouched = plan.getDescription() == null && !plan.isFeatured() && plan.isPublicListed();
            if (untouched) {
                applyPricingCopy(plan);
                planRepository.save(plan);
            }
        }
    }

    private static Feature feature(Row row, int order) {
        Feature f = new Feature();
        f.setCode(row.code());
        f.setName(row.name());
        f.setDescription(row.description());
        f.setCategory(row.category());
        f.setLimitType(row.type());
        f.setUnit(row.unit());
        f.setSortOrder(order);
        f.setActive(true);
        return f;
    }

    /** A plan's cell for a catalog value: {@code false} = not included, a number = limit, a string = note. */
    private static PlanFeature planFeature(SubscriptionPlan plan, Feature feature, Object v) {
        PlanFeature pf = new PlanFeature();
        pf.setPlan(plan);
        pf.setFeature(feature);
        pf.setIncluded(!Boolean.FALSE.equals(v));
        if (v instanceof Integer limit) pf.setLimitValue(limit);
        if (v instanceof String note) pf.setNote(note);
        return pf;
    }

    private SubscriptionPlan plan(SubscriptionType type, String name, String monthly) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setSubscriptionType(type);
        plan.setDisplayName(name);
        plan.setPrice(new BigDecimal(monthly));
        plan.setBasePrice(new BigDecimal(monthly));
        plan.setActive(true);
        applyPricingCopy(plan);
        return planRepository.save(plan);
    }

    /** The public pricing page's pitch per default plan; the Free plan stays off the page. */
    private static void applyPricingCopy(SubscriptionPlan plan) {
        String name = plan.getDisplayName();
        if (plan.getSubscriptionType() == SubscriptionType.FREE) {
            plan.setPublicListed(false);
        } else if (STARTER.equals(name)) {
            plan.setDescription("For small teams getting their people ops in order.");
        } else if (GROWTH.equals(name)) {
            plan.setDescription("For scaling companies that need payroll everywhere.");
            plan.setFeatured(true);
        } else if (ENTERPRISE.equals(name)) {
            plan.setDescription("For groups, resellers and complex org structures.");
        }
    }
}
