package com.humano.config.multitenancy;

import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.repository.tenant.TenantRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work once per active tenant, with {@link TenantContext}
 * set for the duration of each unit. Use this from {@code @Scheduled} ticks
 * and admin commands that must visit every tenant DB — the scheduler has no
 * submitter tenant to inherit, so the work has to drive the context loop
 * itself.
 *
 * <h3>Sequential vs parallel</h3>
 *
 * Pick the mode by fleet size and per-tenant cost:
 *
 * <ul>
 *   <li><b>{@link #forEachActiveTenant}</b> — sequential. Predictable, easy to
 *       trace in logs, no executor pressure. Good for &lt;100 tenants or
 *       cheap-per-tenant ticks.</li>
 *   <li><b>{@link #forEachActiveTenantParallel}</b> — fans out to the
 *       {@code taskExecutor} pool (already decorated with
 *       {@link TenantAwareTaskDecorator}). Bounded by the pool's core/max size,
 *       not by tenant count. Good for thousands of tenants.</li>
 * </ul>
 *
 * <h3>Isolation guarantees</h3>
 *
 * Per-tenant work runs in its own try/catch with its own
 * {@code TenantContext}; a single tenant failure logs and increments the
 * failure counter but does not abort the iteration. Each unit's transaction
 * boundary is the user's responsibility — typically the lambda calls into a
 * {@code @Transactional} service method which starts a fresh transaction.
 */
@Component
public class TenantIteration {

    private static final Logger log = LoggerFactory.getLogger(TenantIteration.class);

    private final TenantRepository tenantRepository;
    private final AsyncTaskExecutor taskExecutor;
    private final TransactionTemplate perTenantTx;

    public TenantIteration(
        TenantRepository tenantRepository,
        @Qualifier("taskExecutor") AsyncTaskExecutor taskExecutor,
        PlatformTransactionManager transactionManager
    ) {
        this.tenantRepository = tenantRepository;
        this.taskExecutor = taskExecutor;
        // Each tenant unit runs in its own REQUIRES_NEW transaction. Important
        // because connections to the routing datasource are bound to the tenant
        // that was current when the tx started — sharing a tx across tenants
        // would write everything to the first tenant's DB.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.perTenantTx = tx;
    }

    /**
     * Summary of a fan-out run. {@code total = succeeded + failed} unless the
     * overall deadline cut some tasks off, in which case stragglers count as
     * failed.
     */
    public record Result(int total, int succeeded, int failed) {
        public boolean allSucceeded() {
            return failed == 0;
        }
    }

    /**
     * Sequential iteration over every ACTIVE tenant.
     *
     * @param work consumer invoked once per tenant subdomain, with that
     *             subdomain already set as the current tenant
     */
    public Result forEachActiveTenant(Consumer<String> work) {
        List<String> subdomains = tenantRepository.findSubdomainsByStatus(TenantStatus.ACTIVE);
        log.debug("Sequentially iterating {} active tenants", subdomains.size());

        int succeeded = 0, failed = 0;
        String previous = TenantContext.getCurrentTenant();
        try {
            for (String subdomain : subdomains) {
                try {
                    TenantContext.setCurrentTenant(subdomain);
                    perTenantTx.executeWithoutResult(status -> work.accept(subdomain));
                    succeeded++;
                } catch (Exception e) {
                    failed++;
                    log.error("Tenant '{}' iteration failed: {}", subdomain, e.getMessage(), e);
                }
            }
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous);
            else TenantContext.clear();
        }
        log.info("Sequential tenant iteration done: {} succeeded, {} failed", succeeded, failed);
        return new Result(subdomains.size(), succeeded, failed);
    }

    /**
     * Parallel fan-out over every ACTIVE tenant via the {@code taskExecutor}
     * pool. Bounded by the pool's max size — at no point will more than
     * {@code pool.maxSize} tenants run concurrently, regardless of fleet size.
     *
     * <p>Per-task tenant context is set both by {@link TenantAwareTaskDecorator}
     * (carrying the submitter's tenant — usually {@code null} here since this
     * is called from a scheduler thread) AND defensively inside the task, so
     * the right tenant is always present when the work runs.
     *
     * @param work            consumer invoked once per tenant subdomain
     * @param overallTimeout  total wall-clock budget for the whole fan-out;
     *                        tasks still running when the budget is exhausted
     *                        are cancelled and counted as failed
     */
    public Result forEachActiveTenantParallel(Consumer<String> work, Duration overallTimeout) {
        List<String> subdomains = tenantRepository.findSubdomainsByStatus(TenantStatus.ACTIVE);
        log.info(
            "Fan-out: submitting {} active tenants to taskExecutor (overall timeout {}s)",
            subdomains.size(),
            overallTimeout.toSeconds()
        );

        List<Future<Boolean>> futures = new ArrayList<>(subdomains.size());
        for (String subdomain : subdomains) {
            futures.add(
                taskExecutor.submit(() -> {
                    String previous = TenantContext.getCurrentTenant();
                    try {
                        TenantContext.setCurrentTenant(subdomain);
                        perTenantTx.executeWithoutResult(status -> work.accept(subdomain));
                        return Boolean.TRUE;
                    } catch (Exception e) {
                        log.error("Tenant '{}' fan-out task failed: {}", subdomain, e.getMessage(), e);
                        return Boolean.FALSE;
                    } finally {
                        if (previous != null) TenantContext.setCurrentTenant(previous);
                        else TenantContext.clear();
                    }
                })
            );
        }

        int succeeded = 0, failed = 0;
        long deadlineNanos = System.nanoTime() + overallTimeout.toNanos();
        for (int i = 0; i < futures.size(); i++) {
            Future<Boolean> f = futures.get(i);
            long remaining = Math.max(0, deadlineNanos - System.nanoTime());
            try {
                if (Boolean.TRUE.equals(f.get(remaining, TimeUnit.NANOSECONDS))) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (TimeoutException e) {
                f.cancel(true);
                failed++;
                log.warn("Tenant '{}' did not complete within fan-out deadline; cancelled", subdomains.get(i));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed++;
                log.warn("Fan-out interrupted; remaining tenants counted as failed");
                for (int j = i + 1; j < futures.size(); j++) {
                    futures.get(j).cancel(true);
                    failed++;
                }
                break;
            } catch (ExecutionException e) {
                failed++;
            }
        }
        log.info("Parallel tenant fan-out done: {} succeeded, {} failed", succeeded, failed);
        return new Result(subdomains.size(), succeeded, failed);
    }
}
