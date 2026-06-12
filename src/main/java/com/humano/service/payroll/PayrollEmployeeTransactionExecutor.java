package com.humano.service.payroll;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides a per-employee transaction boundary for {@link PayrollProcessingService} (M1).
 *
 * <p>Spring's {@code @Transactional} is proxy-based, so a {@code REQUIRES_NEW} method invoked
 * from <em>within</em> the same bean would be a plain self-call and never start a new
 * transaction. Splitting the boundary into this dedicated bean means the call from
 * {@link PayrollProcessingService#calculatePayroll} goes through the proxy and the new
 * transaction is actually created.
 *
 * <p>The class intentionally holds <strong>no</strong> calculation logic and no reference back
 * to {@link PayrollProcessingService}: it only opens the transaction and runs the supplied
 * work. That keeps the dependency one-directional (no cycle) — the caller passes a lambda that
 * re-loads its entities and invokes its own private calculation directly.
 */
@Service
public class PayrollEmployeeTransactionExecutor {

    /**
     * Runs {@code work} in a brand-new tenant transaction.
     *
     * <p>{@code REQUIRES_NEW} suspends any transaction already active on the thread (the
     * orchestration transaction in {@code calculatePayroll}) and begins its own, so the work
     * commits or rolls back independently of the rest of the run. The tenant transaction
     * manager is named explicitly because payroll entities live in the tenant datasource,
     * whereas the application's {@code @Primary} manager is the master one.
     *
     * @param work the per-employee calculation to run; any exception it throws propagates to
     *             the caller after this transaction rolls back.
     */
    @Transactional(transactionManager = "tenantTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void runInNewTransaction(Runnable work) {
        work.run();
    }
}
