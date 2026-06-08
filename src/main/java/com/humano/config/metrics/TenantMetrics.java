package com.humano.config.metrics;

import com.humano.config.multitenancy.TenantContext;
import com.humano.config.multitenancy.TenantDataSourceProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P7.1 — per-tenant Micrometer instrumentation.
 *
 * <p>Single entry point for the four roadmap-named metrics:
 *
 * <ul>
 *   <li>{@code humano.tenant.requests} — counter tagged
 *       {@code tenant, endpoint, status}. Emitted by
 *       {@link TenantMetricsInterceptor#afterCompletion} on every
 *       {@code /api/**} request. Endpoint comes from Spring MVC's
 *       {@code BEST_MATCHING_PATTERN_ATTRIBUTE} so path IDs do not
 *       inflate cardinality.</li>
 *   <li>{@code humano.tenant.db.pool.usage} — multi-gauge with rows
 *       tagged {@code tenant, state} where state is one of
 *       {@code active|idle|total|awaiting}. Refreshed on a 30s
 *       schedule from {@link TenantDataSourceProvider#getPoolStats()}.
 *       Lazy pools that have never been touched do not appear; that
 *       matches the operator's expectation (no traffic ⇒ no pool).</li>
 *   <li>{@code humano.tenant.payroll.run.duration} — timer tagged
 *       {@code tenant}. Started inside
 *       {@code PayrollProcessingService.postPayrollRun} so it only
 *       times the actual POSTED transition + YTD-ledger walk, not
 *       DRAFT/CALCULATED/APPROVED preflight work.</li>
 *   <li>{@code humano.tenant.billing.invoice.amount} — distribution
 *       summary tagged {@code tenant}. Recorded in
 *       {@code InvoiceService.createInvoice} on each invoice
 *       persisted; the tenant tag comes from {@code invoice.getTenant()
 *       .getSubdomain()} because invoice issuance runs in master-DB
 *       context where {@code TenantContext} is null.</li>
 * </ul>
 *
 * <p><b>Tenant-source split.</b> Request counter + payroll timer read
 * {@link TenantContext} on the request thread; both run in tenant-DB
 * context with the context populated. The invoice summary takes the
 * tenant as an explicit parameter because billing runs against the
 * master DB. Mixing those up silently tags every invoice {@code
 * "master"} — the API discourages that by signature.
 *
 * <p><b>Cardinality.</b> {@code tenant} is the only unbounded axis the
 * operator controls; {@code endpoint} is templated (paths, not IDs);
 * {@code status} is the small set of HTTP status codes; {@code state}
 * is fixed-cardinality. The product stays within Prometheus best
 * practice.
 */
@Component
public class TenantMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(TenantMetrics.class);

    public static final String REQUESTS_METRIC = "humano.tenant.requests";
    public static final String DB_POOL_METRIC = "humano.tenant.db.pool.usage";
    public static final String PAYROLL_DURATION_METRIC = "humano.tenant.payroll.run.duration";
    public static final String INVOICE_AMOUNT_METRIC = "humano.tenant.billing.invoice.amount";

    static final String TENANT_TAG = "tenant";
    static final String UNKNOWN_TENANT = "none";

    private final MeterRegistry meterRegistry;
    private final TenantDataSourceProvider dataSourceProvider;
    private final MultiGauge poolUsageGauge;

    public TenantMetrics(MeterRegistry meterRegistry, TenantDataSourceProvider dataSourceProvider) {
        this.meterRegistry = meterRegistry;
        this.dataSourceProvider = dataSourceProvider;
        this.poolUsageGauge = MultiGauge.builder(DB_POOL_METRIC)
            .description("Hikari connection-pool counts per tenant DB (active/idle/total/awaiting)")
            .register(meterRegistry);
    }

    /**
     * Increment the {@code humano.tenant.requests} counter for the
     * current tenant. Endpoint should be the templated path and
     * status the integer HTTP status code as a string.
     */
    public void incrementRequest(String endpoint, String status) {
        String tenant = resolveTenant(TenantContext.getCurrentTenant());
        Counter.builder(REQUESTS_METRIC)
            .description("HTTP requests served per tenant")
            .tag(TENANT_TAG, tenant)
            .tag("endpoint", endpoint)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    /**
     * Start a sample for the payroll-run timer. Caller must call
     * {@link Timer.Sample#stop(Timer)} via {@link #stopPayrollTimer}.
     */
    public Timer.Sample startPayrollTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stop a sample started by {@link #startPayrollTimer()} and record
     * against {@code humano.tenant.payroll.run.duration} tagged with
     * the current tenant.
     */
    public void stopPayrollTimer(Timer.Sample sample) {
        if (sample == null) {
            return;
        }
        String tenant = resolveTenant(TenantContext.getCurrentTenant());
        Timer timer = Timer.builder(PAYROLL_DURATION_METRIC)
            .description("Wall-clock duration of PayrollProcessingService.postPayrollRun")
            .tag(TENANT_TAG, tenant)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Record an invoice amount on
     * {@code humano.tenant.billing.invoice.amount} tagged with the
     * supplied tenant subdomain. Caller passes the tenant explicitly
     * because invoice issuance runs in master-DB context where
     * {@link TenantContext} is null — falling back to it would silently
     * tag every invoice {@code "master"}.
     */
    public void recordInvoiceAmount(String tenantSubdomain, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        DistributionSummary.builder(INVOICE_AMOUNT_METRIC)
            .description("Invoice total amount distribution per tenant (sticker price, pre-tax)")
            .tag(TENANT_TAG, resolveTenant(tenantSubdomain))
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(amount.doubleValue());
    }

    /**
     * Sweep all loaded tenant Hikari pools and refresh the multi-gauge
     * rows. Tenants with no current pool entry are removed from the
     * gauge (MultiGauge.register overwrite=true). Override the schedule
     * via {@code humano.metrics.pool-refresh-cron} if 30s is the wrong
     * cadence for the deployment.
     */
    @Scheduled(fixedRateString = "${humano.metrics.pool-refresh-ms:30000}")
    public void refreshPoolGauges() {
        Map<String, TenantDataSourceProvider.ConnectionPoolStats> stats;
        try {
            stats = dataSourceProvider.getPoolStats();
        } catch (RuntimeException ex) {
            // Never let a metric refresh take down the scheduler.
            LOG.warn("Pool stats refresh failed: {}", ex.getMessage());
            return;
        }
        List<MultiGauge.Row<?>> rows = new ArrayList<>(stats.size() * 4);
        stats.forEach((tenant, s) -> {
            rows.add(MultiGauge.Row.of(Tags.of(TENANT_TAG, tenant, "state", "active"), s.activeConnections()));
            rows.add(MultiGauge.Row.of(Tags.of(TENANT_TAG, tenant, "state", "idle"), s.idleConnections()));
            rows.add(MultiGauge.Row.of(Tags.of(TENANT_TAG, tenant, "state", "total"), s.totalConnections()));
            rows.add(MultiGauge.Row.of(Tags.of(TENANT_TAG, tenant, "state", "awaiting"), s.threadsAwaiting()));
        });
        poolUsageGauge.register(rows, true);
    }

    /**
     * Time {@code unit} into the payroll-duration timer directly when
     * a {@link Timer.Sample} ergonomics aren't a fit (e.g. when
     * recording from a derived measurement). Visible for use by
     * specialised callers; the {@code start/stop} pair is preferred.
     */
    public void recordPayrollDuration(long duration, TimeUnit unit) {
        String tenant = resolveTenant(TenantContext.getCurrentTenant());
        Timer.builder(PAYROLL_DURATION_METRIC)
            .description("Wall-clock duration of PayrollProcessingService.postPayrollRun")
            .tag(TENANT_TAG, tenant)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(duration, unit);
    }

    private static String resolveTenant(String tenant) {
        return (tenant == null || tenant.isBlank()) ? UNKNOWN_TENANT : tenant;
    }
}
